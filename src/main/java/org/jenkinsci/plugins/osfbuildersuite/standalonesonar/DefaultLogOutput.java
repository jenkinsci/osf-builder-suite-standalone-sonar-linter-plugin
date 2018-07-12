package org.jenkinsci.plugins.osfbuildersuite.standalonesonar;

import hudson.model.TaskListener;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;

import javax.annotation.Nonnull;

class DefaultLogOutput implements LogOutput {

    private final TaskListener listener;

    @SuppressWarnings("WeakerAccess")
    public DefaultLogOutput(TaskListener listener) {
        this.listener = listener;
    }

    @Override
    public void log(@Nonnull String formattedMessage, @Nonnull Level level) {
        switch (level) {
            case TRACE:
            case INFO:
            case WARN:
            case DEBUG:
                break;
            case ERROR:
                listener.getLogger().println(formattedMessage);
                break;
        }
    }
}
