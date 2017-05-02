package de.ovgu.ifdefrevolver.bugs.minecommits;

import de.ovgu.ifdefrevolver.bugs.minecommits.main.FindBugfixCommits;
import org.apache.log4j.Logger;
import org.repodriller.RepositoryMining;
import org.repodriller.Study;
import org.repodriller.filter.range.Commits;
import org.repodriller.persistence.csv.CSVFile;
import org.repodriller.scm.GitRepository;
import org.repodriller.scm.SCMRepository;

import java.io.File;
import java.io.IOException;

public class OrderingCommitStudy implements Study {
    private static Logger LOG = Logger.getLogger(OrderingCommitStudy.class);

    private final Config conf;

    public OrderingCommitStudy(Config conf) {
        this.conf = conf;
    }

    @Override
    public void execute() {
        File outFile = new File(conf.outputFileName);
        File canonicalOutFile;
        try {
            canonicalOutFile = outFile.getCanonicalFile();
        } catch (IOException e) {
            canonicalOutFile = outFile;
        }

        if (!conf.forceOverwriteOutput && canonicalOutFile.exists()) {
            throw new RuntimeException("Refusing to overwrite existing output file "
                    + canonicalOutFile.getAbsolutePath() + " (use " + FindBugfixCommits.OPT_FORCE + " to override)");
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

        OrderingCommitVisitor visitor = new OrderingCommitVisitor();
        CSVFile writer = new CSVFile(conf.outputFileName, visitor.getOutputFileHeader());
        SCMRepository repo = GitRepository.singleProject(conf.repoPathName);
        repo.getScm().omitBranches();

        // Raise limit of GIT diff size.  Property: git.maxdiff; default value in repodriller: 100000
        System.setProperty("git.maxdiff", Integer.toString(512 * 1014));

        // @formatter:off
        new RepositoryMining()
                .in(repo)
                .through(Commits.all())
                .withThreads(2)
                .process(visitor, writer)
                .mine();
        // @formatter:on
    }
}
