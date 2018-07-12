package org.jenkinsci.plugins.osfbuildersuite.standalonesonar;

import hudson.AbortException;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import jenkins.MasterToSlaveFileCallable;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.plexus.util.DirectoryScanner;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.osfbuildersuite.standalonesonar.repeatable.ExcludePattern;
import org.kohsuke.stapler.*;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.tracking.IssueTrackable;
import org.sonarsource.sonarlint.core.tracking.Trackable;

import javax.annotation.Nonnull;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@SuppressWarnings("unused")
public class StandaloneSonarLinterBuilder extends Builder implements SimpleBuildStep {

    private String sourcePattern;
    private List<ExcludePattern> excludePatterns;

    @DataBoundConstructor
    public StandaloneSonarLinterBuilder(
            String sourcePattern,
            List<ExcludePattern> excludePatterns) {

        this.sourcePattern = sourcePattern;
        this.excludePatterns = excludePatterns;
    }

    @SuppressWarnings("unused")
    public String getSourcePattern() {
        return sourcePattern;
    }

    @SuppressWarnings("unused")
    @DataBoundSetter
    public void setSourcePattern(String sourcePattern) {
        this.sourcePattern = sourcePattern;
    }

    @SuppressWarnings("unused")
    public List<ExcludePattern> getExcludePatterns() {
        return excludePatterns;
    }

    @SuppressWarnings("unused")
    @DataBoundSetter
    public void setExcludePatterns(List<ExcludePattern> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    @Override
    public void perform(
            @Nonnull Run<?, ?> build,
            @Nonnull FilePath workspace,
            @Nonnull Launcher launcher,
            @Nonnull TaskListener listener) throws InterruptedException, IOException {

        PrintStream logger = listener.getLogger();

        logger.println();
        logger.println(String.format("--[B: %s]--", getDescriptor().getDisplayName()));

        workspace.act(new StandaloneSonarLinterCallable(
                workspace, listener, sourcePattern, excludePatterns
        ));

        logger.println(String.format("--[E: %s]--", getDescriptor().getDisplayName()));
        logger.println();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    @Symbol("osfBuilderSuiteStandaloneSonarLinter")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public DescriptorImpl() {
            load();
        }

        public String getDisplayName() {
            return "OSF Builder Suite :: Standalone Sonar Linter";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

    private static class StandaloneSonarLinterCallable extends MasterToSlaveFileCallable<Void> {

        private static final long serialVersionUID = 1L;

        private final FilePath workspace;
        private final TaskListener listener;
        private final String sourcePattern;
        private final List<ExcludePattern> excludePatterns;

        @SuppressWarnings("WeakerAccess")
        public StandaloneSonarLinterCallable(
                FilePath workspace,
                TaskListener listener,
                String sourcePattern,
                List<ExcludePattern> excludePatterns) {

            this.workspace = workspace;
            this.listener = listener;
            this.sourcePattern = sourcePattern;
            this.excludePatterns = excludePatterns;
        }

        @Override
        public Void invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
            PrintStream logger = listener.getLogger();

            if (StringUtils.isEmpty(sourcePattern)) {
                throw new AbortException("\"Source Pattern\" was not defined!");
            }

            logger.println(String.format("[+] Linting \"%s\"", sourcePattern));

            DirectoryScanner directoryScanner = new DirectoryScanner();
            directoryScanner.setBasedir(dir);
            directoryScanner.setIncludes(new String[] {sourcePattern});

            if (excludePatterns != null) {
                directoryScanner.setExcludes(excludePatterns
                        .stream()
                        .map(ExcludePattern::getExcludePattern)
                        .filter(StringUtils::isNotBlank)
                        .toArray(String[]::new)
                );
            }

            directoryScanner.setCaseSensitive(true);
            directoryScanner.scan();

            List<ClientInputFile> inputFiles = Arrays.stream(directoryScanner.getIncludedFiles())
                    .map(relativePath -> new DefaultClientInputFile(
                            (new File(dir, relativePath)).getAbsolutePath(), relativePath
                    ))
                    .collect(Collectors.toList());

            StandaloneGlobalConfiguration standaloneGlobalConfiguration = StandaloneGlobalConfiguration.builder()
                    .addPlugins(getPlugins())
                    .setLogOutput(new DefaultLogOutput(listener))
                    .build();

            StandaloneSonarLintEngine sonarLintEngine = new StandaloneSonarLintEngineImpl(standaloneGlobalConfiguration);

            StandaloneAnalysisConfiguration analysisConfiguration = new StandaloneAnalysisConfiguration(
                    dir.toPath(),  dir.toPath(), inputFiles, new HashMap<>()
            );

            DefaultIssueListener issueListener = new DefaultIssueListener();

            AnalysisResults analysisResults = sonarLintEngine.analyze(
                    analysisConfiguration, issueListener, null, null
            );

            sonarLintEngine.stop();

            if (!analysisResults.failedAnalysisFiles().isEmpty()) {
                throw new AbortException(String.format("Failed to lint %s !", sourcePattern));
            }

            List<Trackable> trackableIssues = issueListener.get()
                    .stream()
                    .map(IssueTrackable::new)
                    .collect(Collectors.toList());

            if (!trackableIssues.isEmpty()) {
                trackableIssues.stream()
                        .map(Trackable::getIssue)
                        .forEach(issue -> {
                            logger.println();

                            ClientInputFile inputFile = issue.getInputFile();
                            String inputFilePath = "UNKNOWN";

                            if (inputFile != null) {
                                inputFilePath = inputFile.relativePath();
                            }

                            logger.println(String.format(
                                    " ~ [%s/%s/%s] %s@%s,%s-%s,%s",
                                    issue.getSeverity(),
                                    issue.getType(),
                                    issue.getRuleKey(),
                                    inputFilePath,
                                    issue.getStartLine(),
                                    issue.getStartLineOffset(),
                                    issue.getEndLine(),
                                    issue.getEndLineOffset()
                            ));

                            logger.println(String.format("   %s", issue.getRuleName()));
                            logger.println(String.format("   %s", issue.getMessage()));

                            String ruleSpecUrl = getRuleSpecUrl(issue.getRuleKey());

                            if (ruleSpecUrl != null) {
                                logger.println(String.format("   %s", ruleSpecUrl));
                            }
                        });

                logger.println();
                throw new AbortException(String.format("Found %s errors!", trackableIssues.size()));
            }

            logger.println(" + Ok");
            return null;
        }

        private URL[] getPlugins() throws IOException {
            ClassLoader classLoader = getClass().getClassLoader();

            URL jsPluginUrl = classLoader.getResource("plugins/sonar-javascript-plugin-4.1.0.6085.jar");
            if (jsPluginUrl == null) {
                throw new IOException("Error loading JavaScript Sonar plugin!");
            }

            return new URL[] {jsPluginUrl};
        }

        private String getRuleSpecUrl(String ruleKey) {
            Pattern pattern = Pattern.compile("^javascript:S(?<rspec>[0-9]+)$", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(ruleKey);

            if (matcher.matches()) {
                String rspecMatch = matcher.group("rspec");

                if (rspecMatch != null) {
                    return String.format("https://rules.sonarsource.com/javascript/RSPEC-%s", rspecMatch);
                }
            }

            return null;
        }
    }
}

