package org.jenkinsci.plugins.osfbuildersuite.standalonesonar;

import java.util.LinkedList;
import java.util.List;

import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;

public class DefaultIssueListener implements IssueListener {
    private List<Issue> issues = new LinkedList<>();

    @Override
    public void handle(Issue issue) {
        issues.add(issue);
    }

    public List<Issue> get() {
        return issues;
    }
}
