package de.ovgu.ifdefrevolver.bugs.minecommits.main;

import de.ovgu.ifdefrevolver.bugs.minecommits.AbstractSkunkCommitStudy;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitParentsStudy;
import de.ovgu.ifdefrevolver.bugs.minecommits.Config;

public class ListCommitParents extends AbstractSkunkCommitLister {
    public static void main(String[] args) {
        ListCommitParents me = new ListCommitParents();
        me.doMain(args);
    }

    @Override
    protected AbstractSkunkCommitStudy makeNewCommitStudy(Config conf) {
        return new CommitParentsStudy(conf);
    }

    @Override
    protected String getOutputFileCommandLineOptionDescription() {
        return "output file, should be named `commitParents.csv' and be located in the project's results directory";
    }

    @Override
    protected String getCommandLineHelpHeader() {
        return "List commits in a GIT repository, excluding merge commits and excluding commits that don't change .c files.\n\nOptions:\n";
    }
}
