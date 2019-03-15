package org.jenkinsci.plugins.osfbuildersuite.standalonesonar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import org.jenkinsci.plugins.osfbuildersuite.standalonesonar.repeatable.SourcePattern;
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
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@SuppressWarnings("unused")
public class StandaloneSonarLinterBuilder extends Builder implements SimpleBuildStep {

    private List<SourcePattern> sourcePatterns;
    private String reportPath;

    @DataBoundConstructor
    public StandaloneSonarLinterBuilder(
            List<SourcePattern> sourcePatterns,
            String reportPath) {

        this.sourcePatterns = sourcePatterns;
        this.reportPath = reportPath;
    }

    @SuppressWarnings("unused")
    public List<SourcePattern> getSourcePatterns() {
        return sourcePatterns;
    }

    @SuppressWarnings("unused")
    @DataBoundSetter
    public void setSourcePatterns(List<SourcePattern> sourcePatterns) {
        this.sourcePatterns = sourcePatterns;
    }

    @SuppressWarnings("unused")
    public String getReportPath() {
        return reportPath;
    }

    @SuppressWarnings("unused")
    @DataBoundSetter
    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
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
                workspace, listener, sourcePatterns, reportPath
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
        private final List<SourcePattern> sourcePatterns;
        private final String reportPath;

        @SuppressWarnings("WeakerAccess")
        public StandaloneSonarLinterCallable(
                FilePath workspace,
                TaskListener listener,
                List<SourcePattern> sourcePatterns,
                String reportPath) {

            this.workspace = workspace;
            this.listener = listener;
            this.sourcePatterns = sourcePatterns;
            this.reportPath = reportPath;
        }

        @Override
        public Void invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
            PrintStream logger = listener.getLogger();

            if (sourcePatterns == null || sourcePatterns.isEmpty()) {
                throw new AbortException("No \"Source Pattern\" defined!");
            }

            JsonArray errors = new JsonArray();

            for (SourcePattern sourcePattern : sourcePatterns) {
                logger.println(String.format("[+] Linting \"%s\"", sourcePattern.getSourcePattern()));

                DirectoryScanner directoryScanner = new DirectoryScanner();
                directoryScanner.setBasedir(dir);
                directoryScanner.setIncludes(new String[]{sourcePattern.getSourcePattern()});

                if (sourcePattern.getExcludePatterns() != null && !sourcePattern.getExcludePatterns().isEmpty()) {
                    directoryScanner.setExcludes(sourcePattern.getExcludePatterns()
                            .stream()
                            .map(ExcludePattern::getExcludePattern)
                            .filter(StringUtils::isNotBlank)
                            .toArray(String[]::new)
                    );
                }

                directoryScanner.setCaseSensitive(true);
                directoryScanner.scan();

                List<ClientInputFile> inputFiles = Arrays.stream(directoryScanner.getIncludedFiles())
                        .map(relativePath -> new DefaultClientInputFile(dir, relativePath))
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

                analysisResults.failedAnalysisFiles().forEach(clientInputFile -> {
                    logger.println(String.format(
                            " ~ ERROR parsing %s",
                            clientInputFile.relativePath()
                    ));

                    JsonObject error = new JsonObject();
                    error.addProperty("path", clientInputFile.relativePath());
                    error.addProperty("startLine", 0);
                    error.addProperty("startColumn", 0);
                    error.addProperty("message", "Failed to parse!");

                    errors.add(error);
                });

                issueListener.get()
                        .stream()
                        .map(IssueTrackable::new)
                        .map(Trackable::getIssue)
                        .forEach(issue -> {
                            ClientInputFile clientInputFile = issue.getInputFile();
                            String ruleSpecUrl = getRuleSpecUrl(issue.getRuleKey());

                            logger.println(String.format(
                                    " ~ ERROR parsing %s@%s,%s-%s,%s [%s/%s/%s]",
                                    clientInputFile != null ? clientInputFile.relativePath() : "UNKNOWN",
                                    issue.getStartLine(),
                                    issue.getStartLineOffset(),
                                    issue.getEndLine(),
                                    issue.getEndLineOffset(),
                                    issue.getSeverity(),
                                    issue.getType(),
                                    issue.getRuleKey()
                            ));

                            logger.println(String.format("           %s", issue.getRuleName()));
                            logger.println(String.format("           %s", issue.getMessage()));

                            if (ruleSpecUrl != null) {
                                logger.println(String.format("           %s", ruleSpecUrl));
                            }

                            if (clientInputFile != null) {
                                JsonObject error = new JsonObject();
                                error.addProperty("path", clientInputFile.relativePath());
                                error.addProperty("startLine", issue.getStartLine());
                                error.addProperty("startColumn", issue.getStartLineOffset());
                                error.addProperty("endLine", issue.getEndLine());
                                error.addProperty("endColumn", issue.getEndLineOffset());

                                if (ruleSpecUrl != null) {
                                    error.addProperty("message", String.format(
                                            "[%s/%s/%s] %s\n%s\n%s",
                                            issue.getSeverity(),
                                            issue.getType(),
                                            issue.getRuleKey(),
                                            issue.getRuleName(),
                                            issue.getMessage(),
                                            ruleSpecUrl
                                    ));
                                } else {
                                    error.addProperty("message", String.format(
                                            "[%s/%s/%s] %s\n%s",
                                            issue.getSeverity(),
                                            issue.getType(),
                                            issue.getRuleKey(),
                                            issue.getRuleName(),
                                            issue.getMessage()
                                    ));
                                }

                                errors.add(error);
                            }
                        });

                logger.println(" + Done");
                logger.println();
            }

            if (!StringUtils.isEmpty(reportPath)) {
                File reportDir = new File(dir, reportPath);
                if (!reportDir.toPath().normalize().startsWith(dir.toPath())) {
                    throw new AbortException(
                            "Invalid value for \"Report Path\"! The path needs to be inside the workspace!"
                    );
                }

                if (!reportDir.exists()) {
                    if (!reportDir.mkdirs()) {
                        throw new AbortException(String.format("Failed to create %s!", reportDir.getAbsolutePath()));
                    }
                }

                String randomUUID = Long.toHexString(UUID.randomUUID().getMostSignificantBits());
                File reportFile = new File(reportDir, String.format("SonarLint_%s.json", randomUUID));
                if (reportFile.exists()) {
                    throw new AbortException(String.format(
                            "reportFile=%s already exists!",
                            reportFile.getAbsolutePath()
                    ));
                }

                Writer streamWriter = new OutputStreamWriter(new FileOutputStream(reportFile), "UTF-8");
                streamWriter.write(errors.toString());
                streamWriter.close();
            }

            if (errors.size() > 0) {
                throw new AbortException("SonarLint FAILED!");
            }

            return null;
        }

        private URL[] getPlugins() throws IOException {
            ClassLoader classLoader = getClass().getClassLoader();

            URL jsPluginUrl = classLoader.getResource("plugins/sonar-javascript-plugin-5.0.0.6962.jar");
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

