package de.ovgu.skunk.bugs.miner;

import java.io.File;
import java.io.IOException;

import de.ovgu.skunk.bugs.miner.main.FindBugfixCommits;
import org.repodriller.RepositoryMining;
import org.repodriller.Study;
import org.repodriller.persistence.csv.CSVFile;
import org.repodriller.scm.GitRepository;
import org.repodriller.scm.SCMRepository;
import org.repodriller.filter.range.Commits;

public class BugfixCommitStudy implements Study {

    private final Config conf;

    public BugfixCommitStudy(Config conf) {
        this.conf = conf;
    }

    @Override
    public void execute() {
        // do the magic here! ;)
        //
        // // MODE: Word Count
        // if(programMode.contains("wordcount")){
        // final WordCountVisitor wordCountVisitor = new WordCountVisitor();
        // new RepositoryMining()
        // .in(GitRepository.singleProject("/Users/Hannes/Documents/GitHub/irssi"))
        // .through(Commits.all())
        // .withThreads(2)
        // .process(wordCountVisitor, new
        // CSVFile("/Users/Hannes/Documents/GitHub/q0.csv"))
        // .mine();
        //
        // wordCountVisitor.writeWordResults(new
        // CSVFile("/Users/Hannes/Documents/GitHub/wordcount.csv"));
        //
        // // MODE: Bugfix Detection
        // }else if(programMode.contains("bugdetect")){

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

        // @formatter:off
        SCMRepository repo = GitRepository.singleProject(conf.repoPathName);
        new RepositoryMining()
            .in(repo)
            .through(Commits.all())
            .withThreads(2)
            .process(new DevelopersVisitor(conf.bugfixTerms),
                     new CSVFile(conf.outputFileName))
            .mine();
        // @formatter:on
    }
}
