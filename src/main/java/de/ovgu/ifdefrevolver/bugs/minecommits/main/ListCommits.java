package de.ovgu.ifdefrevolver.bugs.minecommits.main;

import de.ovgu.ifdefrevolver.bugs.minecommits.AbstractSkunkCommitStudy;
import de.ovgu.ifdefrevolver.bugs.minecommits.Config;
import de.ovgu.ifdefrevolver.bugs.minecommits.OrderingCommitStudy;

public class ListCommits extends AbstractSkunkCommitLister {
    public static void main(String[] args) {
        ListCommits me = new ListCommits();
        me.doMain(args);
    }

    @Override
    protected AbstractSkunkCommitStudy makeNewCommitStudy(Config conf) {
        return new OrderingCommitStudy(conf);
    }

    @Override
    protected String getOutputFileCommandLineOptionDescription() {
        return "output file, should be named `revisionsFull.csv' and be located in the project's results directory";
    }

    @Override
    protected String getCommandLineHelpHeader() {
        return "List commits in a GIT repository, excluding merge commits and excluding commits that don't change .c files.\n\nOptions:\n";
    }
}
