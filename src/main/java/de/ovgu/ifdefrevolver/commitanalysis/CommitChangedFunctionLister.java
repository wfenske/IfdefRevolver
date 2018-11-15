package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.*;
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
public class CommitChangedFunctionLister {
    private static final Logger LOG = Logger.getLogger(CommitChangedFunctionLister.class);

    private final Repository repo;
    private final String commitId;
    private final AddDelMergingConsumer changedFunctionConsumer;
    private DiffFormatter formatter = null;
    /**
     * All the functions defined in the A-side files of the files that the diffs within this commit modify
     */
    private Map<String, List<Method>> allASideFunctions;
    /**
     * All the functions defined in the B-side files of the files that the diffs within this commit modify
     */
    private Map<String, List<Method>> allBSideFunctions;

    private IFunctionLocationProvider functionLocationProvider;
    private ChangeId currentChangeId;

    public CommitChangedFunctionLister(Repository repo, String commitId,
                                       IFunctionLocationProvider functionLocationProvider,
                                       Consumer<FunctionChangeHunk> changedFunctionConsumer) {
        this.repo = repo;
        this.commitId = commitId;
        this.changedFunctionConsumer = new AddDelMergingConsumer(changedFunctionConsumer);
        this.functionLocationProvider = functionLocationProvider;
    }

    /**
     * <p> Code partially taken from <a href= 'http://stackoverflow.com/questions/19467305/using-the-jgit-how-can-i-retrieve-the-line-numbers-of-added-deleted-lines'>
     * Stackoverflow</a> </p>
     */
    public void listChangedFunctions() {
        LOG.debug("Analyzing commit " + commitId);
        RevWalk rw = null;
        try {
            List<DiffEntry> diffs;
            try {
                rw = new RevWalk(repo);
                RevCommit commit = rw.parseCommit(repo.resolve(commitId));
                final int parentCount = commit.getParentCount();

                if (parentCount == 0) {
                    LOG.warn("Encountered parent-less commit: " + commitId);
                    this.currentChangeId = new ChangeId("", commitId);
                    addFunctionsOfParentLessCommit(commit);
                    return;
                }

                if (parentCount > 1) {
                    LOG.debug("Merge commit: " + commitId);
                }

                for (int iParent = 0; iParent < parentCount; iParent++) {
                    try {
                        ObjectId parentCommitId = commit.getParent(iParent).getId();
                        RevCommit parent = rw.parseCommit(parentCommitId);
                        currentChangeId = new ChangeId(parentCommitId.getName(), commitId);
                        formatter = getDiffFormatterInstance();
                        diffs = formatter.scan(parent.getTree(), commit.getTree());
                        LOG.debug(parentCommitId.name() + " ... " + commitId);

                        Set<String> aSideCFilePaths = getFilenamesOfCFilesModifiedByDiffs(diffs, DiffEntry.Side.OLD);
                        allASideFunctions = listAllFunctionsInModifiedFiles(parent, aSideCFilePaths);

                        Set<String> bSideCFilePaths = getFilenamesOfCFilesModifiedByDiffs(diffs, DiffEntry.Side.NEW);
                        allBSideFunctions = listAllFunctionsInModifiedFiles(commit, bSideCFilePaths);

                        logFilesAndFunctions("A-side", aSideCFilePaths, allASideFunctions);
                        logFilesAndFunctions("B-side", bSideCFilePaths, allBSideFunctions);
                        mapEditsFunctionLocations(diffs);
                        changedFunctionConsumer.mergeAndPublishRemainingHunks();
                    } catch (RuntimeException re) {
                        LOG.warn("Error analyzing diffs for parent " + iParent + " of commit " + commitId, re);
                        continue;
                    } finally {
                        releaseFormatter();
                    }
                }
            } catch (RuntimeException re) {
                LOG.warn("Error analyzing commit " + commitId, re);
                return;
            }
        } catch (IOException ioe) {
            throw new RuntimeException("I/O exception parsing files changed by commit " + commitId, ioe);
        } finally {
            releaseRevisionWalker(rw);
        }
    }

    private void addFunctionsOfParentLessCommit(RevCommit commit) throws IOException {
        final boolean logDebug = LOG.isDebugEnabled();
        FunctionLocationProvider p = new FunctionLocationProvider(repo, commitId);
        allBSideFunctions = p.listFunctionsInDotCFiles(commit);

        for (Map.Entry<String, List<Method>> e : allBSideFunctions.entrySet()) {
            String newPath = e.getKey();
            LOG.debug("File changed by parent-less commit: " + newPath);

            for (Method m : e.getValue()) {
                ChangeHunk ch = new ChangeHunk(currentChangeId, "/dev/null", newPath, 0, 0, m.getGrossLoc());
                FunctionChangeHunk fch = new FunctionChangeHunk(m, ch, FunctionChangeHunk.ModificationType.ADD);
                if (logDebug) {
                    LOG.debug("ADD for parent-less commit: " + fch);
                }
                changedFunctionConsumer.accept(fch);
            }
        }
        changedFunctionConsumer.mergeAndPublishRemainingHunks();
    }

    private void logFilesAndFunctions(String side, Set<String> cFiles, Map<String, List<Method>> functionsByFile) {
        if (LOG.isTraceEnabled()) {
            for (String fn : cFiles) {
                LOG.trace(side + " modified file: " + fn);
            }
            for (List<Method> methods : functionsByFile.values()) {
                for (Method m : methods) {
                    LOG.trace(side + " function: " + m);
                }
            }
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

    private static Set<String> getFilenamesOfCFilesModifiedByDiffs(List<DiffEntry> diffs, DiffEntry.Side side) {
        Set<String> filePaths = new HashSet<>();
        for (DiffEntry diff : diffs) {
            String path = diff.getPath(side);
            if (path.endsWith(".c") || path.endsWith(".C")) {
                filePaths.add(path);
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
        //if (!commitId.equals("34cb9132ef2dae08f91a66015ea5437539a4b557")) return new HashMap<>();
        return functionLocationProvider.listFunctionsInFiles(commitId, state, modifiedFiles);
    }

    //static boolean printedHeader = false;

    private enum DiffType {
        EDIT {
            @Override
            public void handleZeroEdits(CommitChangedFunctionLister self, String oldPath, String newPath, List<Method> oldFunctions, List<Method> newFunctions) {
                // Nothing to do: old and new file are the same, but there are no edits
                return;
            }
        },
        ADD {
            @Override
            public void handleZeroEdits(CommitChangedFunctionLister self, String oldPath, String newPath, List<Method> oldFunctions, List<Method> newFunctions) {
                throw new RuntimeException("Didn't really expect a file addition with zero edits. Commit: " + self.commitId + " old path: " + oldPath + " new path: " + newPath + " old functions: " + oldFunctions + " new functions: " + newFunctions);
            }
        },
        DEL {
            @Override
            public void handleZeroEdits(CommitChangedFunctionLister self, String oldPath, String newPath, List<Method> oldFunctions, List<Method> newFunctions) {
                throw new RuntimeException("Didn't really expect a file deletion with zero edits. Commit: " + self.commitId + " old path: " + oldPath + " new path: " + newPath + " old functions: " + oldFunctions + " new functions: " + newFunctions);
            }
        },
        RENAME {
            @Override
            public void handleZeroEdits(CommitChangedFunctionLister self, String oldPath, String newPath, List<Method> oldFunctions, List<Method> newFunctions) {
                self.publishMoveEvents(oldPath, newPath, oldFunctions, newFunctions);
            }
        };

        public abstract void handleZeroEdits(CommitChangedFunctionLister self, String oldPath, String newPath, List<Method> oldFunctions, List<Method> newFunctions);
    }

    private void publishMoveEvents(String oldPath, String newPath, List<Method> oldFunctions, List<Method> newFunctions) {
        final int numFunctions = oldFunctions.size();
        if (numFunctions != newFunctions.size()) {
            throw new RuntimeException("Expected the same functions in the same order during file rename, but functions numbers don't match. Commit: " + commitId +
                    " old path: " + oldPath + " new path: " + newPath + " old functions: " + oldFunctions +
                    " new functions: " + newFunctions);
        }

        int hunkNo = 0;

        for (int i = 0; i < numFunctions; i++) {
            Method oldFunc = oldFunctions.get(i);
            Method newFunc = newFunctions.get(i);
            if (!oldFunc.uniqueFunctionSignature.equals(newFunc.uniqueFunctionSignature)) {
                throw new RuntimeException("Expected the same functions in the same order during file rename, but signatures don't match. Commit: " + commitId +
                        " old function: " + oldFunc + " new function: " + newFunc);
            }
            ChangeHunk ch = new ChangeHunk(currentChangeId, oldPath, newPath, hunkNo, 0, 0);
            FunctionChangeHunk fh = new FunctionChangeHunk(oldFunc, ch, FunctionChangeHunk.ModificationType.MOVE, newFunc);
            changedFunctionConsumer.accept(fh);
            hunkNo++;
        }
    }

    private void listFunctionChanges(final DiffEntry diff) throws IOException {
        final String oldPath = diff.getOldPath();
        final String newPath = diff.getNewPath();

        final DiffType diffType = getFileDiffType(oldPath, newPath);

        List<Method> oldFunctions = allASideFunctions.get(oldPath);
        List<Method> newFunctions = allBSideFunctions.get(newPath);

//        if ((diffType == DiffType.RENAME) && oldPath.endsWith(".c")) {
//            synchronized (CommitChangedFunctionLister.class) {
//                if (!printedHeader) {
//                    System.out.println("COMMIT_ID,FOLD,FNEW,EDITS,SIGNATURE,OLDP");
//                    printedHeader = true;
//                }
//            }
//
//            EditList edits = formatter.toFileHeader(diff).toEditList();
//            int numEdits = edits.size();
//
//            if (oldFunctions != null)
//                for (Method m : oldFunctions) {
//                    System.out.println(commitId + "," + oldPath + "," + newPath + "," + numEdits + ",\"" + m.functionSignatureXml + "\",0");
//                }
//            if (newFunctions != null)
//                for (Method m : newFunctions) {
//                    System.out.println(commitId + "," + oldPath + "," + newPath + "," + numEdits + ",\"" + m.functionSignatureXml + "\",1");
//                }
//        }

        if (oldFunctions == null) {
            if (newFunctions == null) {
                return;
            }
            oldFunctions = Collections.emptyList();
        }
        // oldFunctions are != null at this point.
        if (newFunctions == null) {
            newFunctions = Collections.emptyList();
        }

        DiffASideFunctionList aSideFunctionList = new DiffASideFunctionList(oldPath, oldFunctions);
        DiffBSideFunctionList bSideFunctionList = new DiffBSideFunctionList(newPath, newFunctions);

        final boolean logDebug = LOG.isDebugEnabled();

        if (logDebug) {
            LOG.debug("--- " + oldPath);
            LOG.debug("+++ " + newPath);
        }

        int iEdit = 0;
        EditList edits = formatter.toFileHeader(diff).toEditList();

        if (edits.isEmpty()) {
            diffType.handleZeroEdits(this, oldPath, newPath, oldFunctions, newFunctions);
        } else {
            Map<Method, Method> movedMethods = new HashMap<>();
            if (diffType == DiffType.RENAME) {
                publishMoveEvents(oldPath, newPath, oldFunctions, newFunctions, movedMethods);
            }

            CommitHunkToFunctionLocationMapper editLocMapper = new CommitHunkToFunctionLocationMapper(currentChangeId,
                    aSideFunctionList,
                    bSideFunctionList,
                    movedMethods,
                    changedFunctionConsumer);

            for (Edit edit : edits) {
                if (logDebug) {
                    LOG.debug("Edit " + (iEdit++) + ": - " + edit.getBeginA() + "," + edit.getEndA() +
                            " + " + edit.getBeginB() + "," + edit.getEndB());
                }
                editLocMapper.accept(edit);
            }

            editLocMapper.handleUntreatedAddedAndDeletedFunctions();
        }
    }

    private void publishMoveEvents(String oldPath, String newPath, List<Method> oldFunctions, List<Method> newFunctions, Map<Method, Method> movedMethods) {
        final boolean logDebug = LOG.isDebugEnabled();

        Map<String, Method> oldFunctionsBySignature = new HashMap<>();
        Map<String, Method> newFunctionsBySignature = new HashMap<>();
        oldFunctions.forEach((m) -> oldFunctionsBySignature.put(m.uniqueFunctionSignature, m));
        newFunctions.forEach((m) -> newFunctionsBySignature.put(m.uniqueFunctionSignature, m));

        Set<String> deletedSignatures = new HashSet<>(oldFunctionsBySignature.keySet());
        Set<String> createdSignatures = new HashSet<>(newFunctionsBySignature.keySet());

        Set<String> movedSignatures = new HashSet<>();
        movedSignatures.addAll(oldFunctionsBySignature.keySet());
        movedSignatures.retainAll(newFunctionsBySignature.keySet());

        deletedSignatures.removeAll(movedSignatures);
        createdSignatures.removeAll(movedSignatures);

        for (String movedSignature : movedSignatures) {
            ChangeHunk ch = new ChangeHunk(currentChangeId, oldPath, newPath, 0, 0, 0);
            Method oldFunc = oldFunctionsBySignature.get(movedSignature);
            Method newFunc = newFunctionsBySignature.get(movedSignature);
            FunctionChangeHunk fh = new FunctionChangeHunk(oldFunc, ch, FunctionChangeHunk.ModificationType.MOVE, newFunc);
            if (logDebug) {
                LOG.debug("Publishing inferred MOVE event for function " + movedSignature + " from file " + oldFunc.filePath + " to " + newFunc.filePath + ".");
            }
            changedFunctionConsumer.accept(fh);
            movedMethods.put(oldFunc, newFunc);
        }

        for (String signature : deletedSignatures) {
            Method func = oldFunctionsBySignature.get(signature);
            FunctionChangeHunk fh = FunctionChangeHunk.makePseudoDel(currentChangeId, oldPath, newPath, func);
            changedFunctionConsumer.accept(fh);
        }

        for (String signature : createdSignatures) {
            Method func = newFunctionsBySignature.get(signature);
            FunctionChangeHunk fh = FunctionChangeHunk.makePseudoAdd(currentChangeId, oldPath, newPath, func, Optional.empty());
            changedFunctionConsumer.accept(fh);
        }
    }

    private DiffType getFileDiffType(String oldPath, String newPath) {
        DiffType diffType = DiffType.EDIT;
        if (!oldPath.equals(newPath)) {
            if (oldPath.equals("/dev/null"))
                diffType = DiffType.ADD;
            else if (newPath.equals("/dev/null"))
                diffType = DiffType.DEL;
            else
                diffType = DiffType.RENAME;
        }
        return diffType;
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


        IFunctionLocationProvider functionLocationProvider = new EagerFunctionLocationProvider(repo);

        for (String commitId : args) {
            CommitChangedFunctionLister lister = new CommitChangedFunctionLister(repo, commitId,
                    functionLocationProvider, changedFunctionConsumer);
            lister.listChangedFunctions();
        }

        GitUtil.silentlyCloseGitAndRepo(git, repo);
    }
}
