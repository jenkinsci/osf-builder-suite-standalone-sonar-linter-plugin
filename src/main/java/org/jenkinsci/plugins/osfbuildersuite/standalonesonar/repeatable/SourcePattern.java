package org.jenkinsci.plugins.osfbuildersuite.standalonesonar.repeatable;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.List;

public class SourcePattern implements Serializable, Describable<SourcePattern> {

    private final String sourcePattern;
    private final List<ExcludePattern> excludePatterns;

    @DataBoundConstructor
    public SourcePattern(String sourcePattern, List<ExcludePattern> excludePatterns) {
        this.sourcePattern = sourcePattern;
        this.excludePatterns = excludePatterns;
    }

    public String getSourcePattern() {
        return sourcePattern;
    }

    public List<ExcludePattern> getExcludePatterns() {
        return excludePatterns;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.get().getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SourcePattern> {

        @Override
        public String getDisplayName() {
            return "OSF Builder Suite :: Standalone Sonar Linter (SourcePattern)";
        }
    }
}
