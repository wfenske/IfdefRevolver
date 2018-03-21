package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import org.apache.log4j.Logger;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Created by wfenske on 14.04.17.
 */
class CommitChangedFunctionLister {
    private static final Logger LOG = Logger.getLogger(CommitChangedFunctionLister.class);

    private final Repository repo;
    private final String commitId;
    private final Consumer<FunctionChangeHunk> changedFunctionConsumer;
    private DiffFormatter formatter = null;
    /**
     * All the functions defined in the A-side files of the files that the diffs within this commit modify
     */
    private Map<String, List<Method>> allASideFunctions;
    private final IFunctionLocationProvider functionLocationProvider;

    public CommitChangedFunctionLister(Repository repo, String commitId,
                                       IFunctionLocationProvider functionLocationProvider, Consumer<FunctionChangeHunk> changedFunctionConsumer) {
        this.repo = repo;
        this.commitId = commitId;
        this.changedFunctionConsumer = changedFunctionConsumer;
        this.functionLocationProvider = functionLocationProvider;
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
            } catch (RuntimeException re) {
                LOG.warn("Error analyzing commit " + commitId, re);
                return;
            }
            mapEditsToASideFunctionLocations(diffs);
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

    private void mapEditsToASideFunctionLocations(List<DiffEntry> diffs) throws IOException {
        LOG.debug("Mappings edits to A-side function locations");
        for (DiffEntry diff : diffs) {
            listChangedFunctions(diff);
        }
    }

    private Set<String> getFilenamesOfCFilesModifiedByDiffsASides(List<DiffEntry> diffs) {
        Set<String> aSideCFilePaths = new HashSet<>();
        for (DiffEntry diff : diffs) {
            String oldPath = diff.getOldPath();
            if (oldPath.endsWith(".c") || oldPath.endsWith(".C")) {
                aSideCFilePaths.add(oldPath);
            }
        }
        return aSideCFilePaths;
    }

    private DiffFormatter getDiffFormatterInstance() {
        DiffFormatter formatter;
        formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
        formatter.setRepository(repo);
        formatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
        formatter.setDetectRenames(true);
        return formatter;
    }

    private Map<String, List<Method>> listAllFunctionsInModifiedFiles(RevCommit stateBeforeCommit, Set<String> modifiedFiles) throws IOException {
        LOG.debug("Parsing all A-side functions");
        if (modifiedFiles.isEmpty()) {
            return Collections.emptyMap();
        }

        return functionLocationProvider.listFunctionsInFiles(commitId, stateBeforeCommit, modifiedFiles);
    }

    private void listChangedFunctions(final DiffEntry diff) throws IOException {
        final String oldPath = diff.getOldPath();
        final String newPath = diff.getNewPath();

        LOG.debug("--- " + oldPath);
        LOG.debug("+++ " + newPath);

        List<Method> functions = allASideFunctions.get(oldPath);
        if (functions == null) {
            return;
        }

        CommitHunkToFunctionLocationMapper editLocMapper = new CommitHunkToFunctionLocationMapper(commitId,
                oldPath, functions,
                newPath, Collections.emptyList(),
                changedFunctionConsumer);

        for (Edit edit : formatter.toFileHeader(diff).toEditList()) {
            LOG.debug("- " + edit.getBeginA() + "," + edit.getEndA() +
                    " + " + edit.getBeginB() + "," + edit.getEndB());
            editLocMapper.accept(edit);
        }
    }

}
