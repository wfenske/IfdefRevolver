package de.ovgu.ifdefrevolver.bugs.minecommits;

import de.ovgu.ifdefrevolver.bugs.minecommits.main.ListCommits;
import org.repodriller.scm.SCMRepository;

import java.util.Optional;

public class CommitParentsStudy extends AbstractSkunkCommitStudy {

    public CommitParentsStudy(Config conf) {
        super(conf);
    }

    @Override
    protected Optional<Integer> getMaxDiffSize() {
        return Optional.of(1024 * 1014);
    }

    @Override
    protected void configureRepository(SCMRepository repo) {
        super.configureRepository(repo);
        repo.getScm().omitModifications();
    }

    @Override
    protected ICommitVisitorWithOutputFileHeader makeNewVisitor() {
        return new CommitParentsVisitor();
    }

    @Override
    protected String getForceCommandLineOptionName() {
        return ListCommits.OPT_FORCE;
    }
}
