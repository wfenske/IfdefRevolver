package de.ovgu.ifdefrevolver.bugs.minecommits;

import org.repodriller.RepositoryMining;
import org.repodriller.Study;
import org.repodriller.filter.range.Commits;
import org.repodriller.persistence.csv.CSVFile;
import org.repodriller.scm.GitRepository;
import org.repodriller.scm.SCMRepository;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public abstract class AbstractSkunkCommitStudy implements Study {
    protected final Config conf;
    private boolean studySuccessful = false;

    public AbstractSkunkCommitStudy(Config conf) {
        this.conf = conf;
    }

    @Override
    public void execute() {
        studySuccessful = false;
        File outFile = new File(conf.outputFileName);
        File canonicalOutFile;
        try {
            canonicalOutFile = outFile.getCanonicalFile();
        } catch (IOException e) {
            canonicalOutFile = outFile;
        }

        if (!conf.forceOverwriteOutput && canonicalOutFile.exists()) {
            throw new RuntimeException("Refusing to overwrite existing output file "
                    + canonicalOutFile.getAbsolutePath() + " (use " + getForceCommandLineOptionName() + " to override)");
        }
        if (canonicalOutFile.isDirectory()) {
            throw new RuntimeException("Output file " + canonicalOutFile.getAbsolutePath() + " is a directory");
        }

        final File canonicalOutFileParentDir = canonicalOutFile.getParentFile();
        if (!canonicalOutFileParentDir.isDirectory()) {
            if (!canonicalOutFileParentDir.mkdirs()) {
                throw new RuntimeException(
                        "Failed to create directories for output file " + canonicalOutFileParentDir.getAbsolutePath());
            }
        }

        File repoFile = new File(conf.repoPathName);
        File canonicalRepoFile;
        try {
            canonicalRepoFile = repoFile.getCanonicalFile();
        } catch (IOException e) {
            canonicalRepoFile = repoFile;
        }

        if (!canonicalRepoFile.exists()) {
            throw new RuntimeException("Repository " + canonicalRepoFile.getAbsolutePath() + " does not exist");
        }
        if (!canonicalRepoFile.isDirectory()) {
            throw new RuntimeException("Repository " + canonicalRepoFile.getAbsolutePath() + " is not a directory");
        }

        ICommitVisitorWithOutputFileHeader visitor = makeNewVisitor();
        CSVFile writer = new CSVFile(conf.outputFileName, visitor.getOutputFileHeader());
        SCMRepository repo = GitRepository.singleProject(conf.repoPathName);
        configureRepository(repo);

        // Raise limit of GIT diff size.  Property: git.maxdiff; default value in repodriller: 100000
        final Optional<Integer> maxDiffSize = getMaxDiffSize();
        if (maxDiffSize.isPresent()) {
            System.setProperty("git.maxdiff", Integer.toString(maxDiffSize.get()));
        }

        // @formatter:off
        new RepositoryMining()
                .in(repo)
                .through(Commits.all())
                .withThreads(2)
                .process(visitor, writer)
                .mine();
        // @formatter:on
        studySuccessful = true;
    }

    protected void configureRepository(SCMRepository repo) {
        repo.getScm().omitBranches();
    }

    public boolean wasStudySuccessful() {
        return studySuccessful;
    }

    /**
     * Allows to control the maximum size of a diff that repodriller will parse. Commits whose diffs are larger than
     * that will not be processed.
     *
     * @return A positive integer or nothing, in which case an implementation-dependent default will be used
     */
    protected abstract Optional<Integer> getMaxDiffSize();

    protected abstract ICommitVisitorWithOutputFileHeader makeNewVisitor();

    protected abstract String getForceCommandLineOptionName();
}
