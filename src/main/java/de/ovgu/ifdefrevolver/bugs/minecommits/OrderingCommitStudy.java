package de.ovgu.ifdefrevolver.bugs.minecommits;

import de.ovgu.ifdefrevolver.bugs.minecommits.main.ListCommits;

import java.util.Optional;

public class OrderingCommitStudy extends AbstractSkunkCommitStudy {

    public OrderingCommitStudy(Config conf) {
        super(conf);
    }

    @Override
    protected Optional<Integer> getMaxDiffSize() {
        return Optional.of(512 * 1014);
    }

    @Override
    protected ICommitVisitorWithOutputFileHeader makeNewVisitor() {
        return new OrderingCommitVisitor();
    }

    @Override
    protected String getForceCommandLineOptionName() {
        return ListCommits.OPT_FORCE;
    }
}
