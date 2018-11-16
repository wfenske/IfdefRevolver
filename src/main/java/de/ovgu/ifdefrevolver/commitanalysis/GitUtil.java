package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.detection.input.PositionalXmlReader;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class GitUtil {
    private static Logger LOG = Logger.getLogger(GitUtil.class);

    private GitUtil() {
    }

    public static Map<String, List<Method>> listFunctionsAtCurrentCommit(String repoDir, String commitId) {
        final PositionalXmlReader xmlReader = new PositionalXmlReader();

        Git git;
        try {
            git = Git.open(new File(repoDir));
        } catch (IOException ioe) {
            LOG.warn("Failed to open repository " + repoDir, ioe);
            return null;
        }

        Repository repo = git.getRepository();
        try {
            RevWalk rw = null;
            try {
                FunctionLocationProvider p = new FunctionLocationProvider(repo, commitId, xmlReader);
                rw = new RevWalk(repo);
                RevCommit rCommit = rw.parseCommit(repo.resolve(commitId));
                return p.listFunctionsInDotCFiles(rCommit);
            } catch (IOException ioe) {
                LOG.warn("I/O exception parsing files changed by commit " + commitId, ioe);
                return null;
            } finally {
                try {
                    if (rw != null) rw.release();
                } catch (RuntimeException e) {
                    LOG.warn("Problem releasing revision walker for commit " + commitId, e);
                }
            }
        } finally {
            GitUtil.silentlyCloseGitAndRepo(git, repo);
        }
    }

    public static void silentlyCloseGitAndRepo(Git git, Repository repo) {
        if (repo != null) {
            try {
                repo.close();
            } finally {
                try {
                    if (git != null) {
                        try {
                            git.close();
                        } finally {
                            git = null;
                        }
                    }
                } finally {
                    repo = null;
                }
            }
        }
    }
}
