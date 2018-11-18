package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.detection.input.PositionalXmlReader;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;

public final class GitUtil {
    private static Logger LOG = Logger.getLogger(GitUtil.class);

    private GitUtil() {
    }

    public static Calendar getAuthorDateOfCommit(String repoDir, String commitId) {
        return computeUsingRepoAndCommit(repoDir, commitId, (repo, revCommit) -> {
            Calendar authorDate = new GregorianCalendar();
            authorDate.setTime(revCommit.getAuthorIdent().getWhen());
            authorDate.setTimeZone(revCommit.getAuthorIdent().getTimeZone());
            return authorDate;
        }).get();
    }

    public static Map<String, List<Method>> listFunctionsAtCurrentCommit(String repoDir, String commitId) {
        final PositionalXmlReader xmlReader = new PositionalXmlReader();
        return computeUsingRepoAndCommit(repoDir, commitId, (repo, revCommit) -> {
            FunctionLocationProvider p = new FunctionLocationProvider(repo, commitId, xmlReader);
            try {
                final Map<String, List<Method>> result = p.listFunctionsInDotCFiles(revCommit);
                return result;
            } catch (IOException ioe) {
                LOG.warn("I/O exception parsing commit " + commitId + " in repository " + repoDir, ioe);
                return null;
            }
        }).get();
    }

    protected static <TResult> Optional<TResult> computeUsingRepoAndCommit(String repoDir, String commitId, BiFunction<Repository, RevCommit, TResult> produceResultUsingCommit) {
        Git git;
        try {
            git = Git.open(new File(repoDir));
        } catch (IOException ioe) {
            LOG.warn("Failed to open repository " + repoDir, ioe);
            return Optional.empty();
        }

        Repository repo = git.getRepository();
        try {
            RevWalk rw = null;
            try {
                rw = new RevWalk(repo);
                RevCommit rCommit;
                try {
                    rCommit = rw.parseCommit(repo.resolve(commitId));
                } catch (RevisionSyntaxException | IOException e) {
                    LOG.warn("Error resolving commit " + commitId, e);
                    return Optional.empty();
                }
                TResult result = produceResultUsingCommit.apply(repo, rCommit);
                return Optional.ofNullable(result);
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
