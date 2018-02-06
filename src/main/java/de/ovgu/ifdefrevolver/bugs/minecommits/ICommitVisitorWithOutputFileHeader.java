package de.ovgu.ifdefrevolver.bugs.minecommits;

import org.repodriller.scm.CommitVisitor;

public interface ICommitVisitorWithOutputFileHeader extends CommitVisitor {
    String[] getOutputFileHeader();
}
