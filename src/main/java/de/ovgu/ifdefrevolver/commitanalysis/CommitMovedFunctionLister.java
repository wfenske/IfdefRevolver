package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Created by wfenske on 14.04.17.
 */
public class CommitMovedFunctionLister {
    private static final Logger LOG = Logger.getLogger(CommitMovedFunctionLister.class);

    private final Repository repo;
    private final String commitId;
    private final Consumer<FunctionChangeHunk> changedFunctionConsumer;
    private DiffFormatter formatter = null;
    /**
     * All the functions defined in the A-side files of the files that the diffs within this commit modify
     */
    private Map<String, List<Method>> allASideFunctions;
    /**
     * All the functions defined in the B-side files of the files that the diffs within this commit modify
     */
    private Map<String, List<Method>> allBSideFunctions;

    public CommitMovedFunctionLister(Repository repo, String commitId, Consumer<FunctionChangeHunk> changedFunctionConsumer) {
        this.repo = repo;
        this.commitId = commitId;
        this.changedFunctionConsumer = changedFunctionConsumer;
    }

    /**
     * <p> Code partially taken from <a href= 'http://stackoverflow.com/questions/19467305/using-the-jgit-how-can-i-retrieve-the-line-numbers-of-added-deleted-lines'>
     * Stackoverflow</a> </p>
     */
    public void listChangedFunctions() {
        LOG.info("Analyzing commit " + commitId);
        RevWalk rw = null;
        try {
            List<DiffEntry> diffs;
            try {
                rw = new RevWalk(repo);
                RevCommit commit = rw.parseCommit(repo.resolve(commitId));
                final int parentCount = commit.getParentCount();
                if (parentCount == 0) {
                    LOG.warn("Ignoring commit " + commitId + ": no parents");
                    return;
                }
                if (parentCount > 1) {
                    LOG.info("Ignoring merge commit " + commitId + ": expected exactly one parent, got " + parentCount);
                    return;
                }
                ObjectId parentCommitId = commit.getParent(0).getId();
                RevCommit parent = rw.parseCommit(parentCommitId);
                formatter = getDiffFormatterInstance();
                diffs = formatter.scan(parent.getTree(), commit.getTree());
                LOG.debug(parentCommitId.name() + " ... " + commitId);

                Set<String> aSideCFilePaths = getFilenamesOfCFilesModifiedByDiffsASides(diffs);
                allASideFunctions = listAllFunctionsInModifiedFiles(parent, aSideCFilePaths);

                Set<String> bSideCFilePaths = getFilenamesOfCFilesModifiedByDiffsBSides(diffs);
                allBSideFunctions = listAllFunctionsInModifiedFiles(commit, bSideCFilePaths);
                if (LOG.isTraceEnabled()) {
                    for (String fn : bSideCFilePaths) {
                        LOG.trace("B-side modified file: " + fn);
                    }
                    for (List<Method> methods : allBSideFunctions.values()) {
                        for (Method m : methods) {
                            LOG.trace("B-side function: " + m);
                        }
                    }
                }
            } catch (RuntimeException re) {
                LOG.warn("Error analyzing commit " + commitId, re);
                return;
            }
            mapEditsFunctionLocations(diffs);
        } catch (IOException ioe) {
            throw new RuntimeException("I/O exception parsing files changed by commit " + commitId, ioe);
        } finally {
            releaseFormatter();
            releaseRevisionWalker(rw);
        }
    }

    private void releaseRevisionWalker(RevWalk rw) {
        try {
            if (rw != null) rw.release();
        } catch (RuntimeException e) {
            LOG.warn("Problem releasing revision walker for commit " + commitId, e);
        }
    }

    private void releaseFormatter() {
        try {
            if (formatter != null) formatter.release();
        } catch (RuntimeException e) {
            LOG.warn("Problem releasing diff formatter for commit " + commitId, e);
        }
    }

    private void mapEditsFunctionLocations(List<DiffEntry> diffs) throws IOException {
        LOG.debug("Mapping edits to function locations");
        for (DiffEntry diff : diffs) {
            listFunctionChanges(diff);
        }
    }

    private Set<String> getFilenamesOfCFilesModifiedByDiffsASides(List<DiffEntry> diffs) {
        return getFilenamesOfCFilesModifiedByDiffs(diffs, DiffEntry.Side.OLD);
    }

    private Set<String> getFilenamesOfCFilesModifiedByDiffsBSides(List<DiffEntry> diffs) {
        return getFilenamesOfCFilesModifiedByDiffs(diffs, DiffEntry.Side.NEW);
    }

    private Set<String> getFilenamesOfCFilesModifiedByDiffs(List<DiffEntry> diffs, DiffEntry.Side side) {
        Set<String> filePaths = new HashSet<>();
        for (DiffEntry diff : diffs) {
            String oldPath = diff.getPath(side);
            if (oldPath.endsWith(".c") || oldPath.endsWith(".C")) {
                filePaths.add(oldPath);
            }
        }
        return filePaths;
    }

    private DiffFormatter getDiffFormatterInstance() {
        DiffFormatter formatter;
        formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
        formatter.setRepository(repo);
        formatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
        formatter.setDetectRenames(true);
        return formatter;
    }

    private Map<String, List<Method>> listAllFunctionsInModifiedFiles(RevCommit state, Set<String> modifiedFiles) throws IOException {
        LOG.debug("Parsing all functions in files touched by " + state.getId());
        if (modifiedFiles.isEmpty()) {
            return Collections.emptyMap();
        }

        FunctionLocationProvider functionLocationProvider = new FunctionLocationProvider(repo);
        return functionLocationProvider.listFunctionsInFiles(commitId, state, modifiedFiles);
    }

    private void listFunctionChanges(final DiffEntry diff) throws IOException {
        final String oldPath = diff.getOldPath();
        final String newPath = diff.getNewPath();

        LOG.debug("--- " + oldPath);
        LOG.debug("+++ " + newPath);

        List<Method> oldFunctions = allASideFunctions.get(oldPath);
        List<Method> newFunctions = allBSideFunctions.get(newPath);

        if (oldFunctions == null) oldFunctions = Collections.emptyList();
        if (newFunctions == null) newFunctions = Collections.emptyList();

        CommitHunkToFunctionLocationMapper editLocMapper = new CommitHunkToFunctionLocationMapper(commitId,
                oldPath, oldFunctions,
                newPath, newFunctions,
                changedFunctionConsumer);

        for (Edit edit : formatter.toFileHeader(diff).toEditList()) {
            LOG.debug("- " + edit.getBeginA() + "," + edit.getEndA() +
                    " + " + edit.getBeginB() + "," + edit.getEndB());
            editLocMapper.accept(edit);
        }
    }

    private static class SingleEditConsumer implements Consumer<FunctionChangeHunk> {
        List<FunctionChangeHunk> hunksForEdit = new ArrayList<>();

        @Override
        public void accept(FunctionChangeHunk functionChangeHunk) {
            hunksForEdit.add(functionChangeHunk);
        }
    }

    public static void main(String[] args) {
        Git git = null;
        try {
            git = Git.open(new File("."));
        } catch (IOException ioe) {
            System.out.flush();
            System.err.println("Failed to open repository " + ioe);
            ioe.printStackTrace(System.err);
            System.err.flush();
            System.exit(1);
        }
        Repository repo = git.getRepository();

        Consumer<FunctionChangeHunk> changedFunctionConsumer = new Consumer<FunctionChangeHunk>() {
            @Override
            public void accept(FunctionChangeHunk functionChangeHunk) {
                LOG.info("ACCEPT: " + functionChangeHunk);
            }
        };

        for (String commitId : args) {
            CommitMovedFunctionLister lister = new CommitMovedFunctionLister(repo, commitId, changedFunctionConsumer);
            lister.listChangedFunctions();
        }

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
