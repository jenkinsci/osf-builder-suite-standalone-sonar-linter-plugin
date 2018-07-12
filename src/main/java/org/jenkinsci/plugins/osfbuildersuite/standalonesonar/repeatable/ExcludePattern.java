package org.jenkinsci.plugins.osfbuildersuite.standalonesonar.repeatable;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class ExcludePattern implements Serializable, Describable<ExcludePattern> {

    private final String excludePattern;

    @DataBoundConstructor
    public ExcludePattern(String excludePattern) {
        this.excludePattern = excludePattern;
    }

    public String getExcludePattern() {
        return excludePattern;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.get().getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<ExcludePattern> {

        @Override
        public String getDisplayName() {
            return "OSF Builder Suite :: Standalone Sonar Linter (ExcludePattern)";
        }
    }
}
