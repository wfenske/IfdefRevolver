package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.log4j.Logger;
import org.eclipse.jgit.diff.Edit;

import java.util.*;
import java.util.function.Consumer;

/**
 * Created by wfenske on 14.04.17.
 */
class CommitHunkToFunctionLocationMapper implements Consumer<Edit> {
    private static final Logger LOG = Logger.getLogger(CommitHunkToFunctionLocationMapper.class);
    /**
     * A-side file path of the file being modified
     */
    final String oldPath;
    final List<Method> functionsInOldPathByOccurrence;
    /**
     * B-side file path of the file being modified
     */
    final String newPath;
    final List<Method> functionsInNewPathByOccurrence;

    private static abstract class AppearedAndDisappearedFunctionHandler {
        final ChangeId changeId;
        final DiffASideFunctionList aSideFunctionList;
        final DiffBSideFunctionList bSideFunctionList;

        public AppearedAndDisappearedFunctionHandler(ChangeId changeId, DiffASideFunctionList aSideFunctionList, DiffBSideFunctionList bSideFunctionList) {
            this.changeId = changeId;
            this.aSideFunctionList = aSideFunctionList;
            this.bSideFunctionList = bSideFunctionList;
        }

        void initialize() {
            // hook method
        }

        public abstract Set<Method> getUntreatedAppearedFunctions();

        public abstract Set<Method> getUntreatedDisappearedFunctions();

        public abstract void markFunctionAsDeleted(Method delFunction);

        public abstract void markFunctionAsAdded(Method addFunction);

        public abstract boolean isAppearedFunction(Method f);

        public abstract boolean isDisappearedFunction(Method f);

        public abstract void adjustAppearedFunctionASideStartLocations(Edit edit);

        public abstract int getASideStartLocationOfAppearedFunction(Method f);
    }

    private static class NullAppearedAndDisappearedFunctionHandler extends AppearedAndDisappearedFunctionHandler {
        public NullAppearedAndDisappearedFunctionHandler(ChangeId changeId, DiffASideFunctionList aSideFunctionList, DiffBSideFunctionList bSideFunctionList) {
            super(changeId, aSideFunctionList, bSideFunctionList);
        }

        @Override
        public Set<Method> getUntreatedAppearedFunctions() {
            return Collections.emptySet();
        }

        @Override
        public Set<Method> getUntreatedDisappearedFunctions() {
            return Collections.emptySet();
        }

        @Override
        public void markFunctionAsDeleted(Method delFunction) {
            return;
        }

        @Override
        public void markFunctionAsAdded(Method addFunction) {
            return;
        }

        @Override
        public boolean isAppearedFunction(Method f) {
            return false;
        }

        @Override
        public boolean isDisappearedFunction(Method f) {
            return false;
        }

        @Override
        public void adjustAppearedFunctionASideStartLocations(Edit edit) {
            // nothing to do
        }

        @Override
        public int getASideStartLocationOfAppearedFunction(Method f) {
            return f.start1;
        }
    }

    private static class SimpleAppearedAndDisappearedFunctionHandler extends AppearedAndDisappearedFunctionHandler {
        private Set<Method> appearedFunctions;
        private Set<Method> disappearedFunctions;
        private Set<Method> untreatedAppearedFunctions;
        private Set<Method> untreatedDisappearedFunctions;
        private Map<Method, Integer> appearedFunctionASideStartLocation;

        public SimpleAppearedAndDisappearedFunctionHandler(ChangeId changeId, DiffASideFunctionList aSideFunctionList, DiffBSideFunctionList bSideFunctionList) {
            super(changeId, aSideFunctionList, bSideFunctionList);
        }

        @Override
        public void initialize() {
            appearedFunctions = new LinkedHashSet<>(bSideFunctionList.getFunctionsByOccurrence());
            appearedFunctions.removeAll(aSideFunctionList.getFunctionsByOccurrence());
            disappearedFunctions = new LinkedHashSet<>(aSideFunctionList.getFunctionsByOccurrence());
            disappearedFunctions.removeAll(bSideFunctionList.getFunctionsByOccurrence());

            appearedFunctionASideStartLocation = new LinkedHashMap<>();
            for (Method f : appearedFunctions) {
                appearedFunctionASideStartLocation.put(f, f.start1);
            }

            untreatedAppearedFunctions = new HashSet<>(appearedFunctions);
            untreatedDisappearedFunctions = new HashSet<>(disappearedFunctions);

            logAfterInit();
        }

        private void logAfterInit() {
            if (LOG.isDebugEnabled()) {
                for (Method m : appearedFunctions) {
                    LOG.debug("Commit " + changeId + "    adds function: " + m);
                }
                for (Method m : disappearedFunctions) {
                    LOG.debug("Commit " + changeId + " deletes function: " + m);
                }
            }
        }

        @Override
        public Set<Method> getUntreatedAppearedFunctions() {
            return untreatedAppearedFunctions;
        }

        @Override
        public Set<Method> getUntreatedDisappearedFunctions() {
            return untreatedDisappearedFunctions;
        }

        @Override
        public void markFunctionAsDeleted(Method delFunction) {
            if (delFunction == null) return;
            untreatedDisappearedFunctions.remove(delFunction);
        }

        @Override
        public void markFunctionAsAdded(Method addFunction) {
            if (addFunction == null) return;
            untreatedAppearedFunctions.remove(addFunction);
        }

        @Override
        public boolean isAppearedFunction(Method f) {
            return appearedFunctions.contains(f);
        }

        @Override
        public boolean isDisappearedFunction(Method f) {
            return disappearedFunctions.contains(f);
        }

        @Override
        public void adjustAppearedFunctionASideStartLocations(Edit edit) {
            final int delta = edit.getLengthB() - edit.getLengthA();
            final int beginB = edit.getBeginB();
            for (Map.Entry<Method, Integer> e : appearedFunctionASideStartLocation.entrySet()) {
                final Method f = e.getKey();
                final int fStart0 = f.start1 - 1;
                if (fStart0 < beginB) continue;
                // NOTE, 2018-11-13, wf: The subtraction is actually correct: If more lines were
                // deleted than added, delta will be negative, and all B-side function
                // locations will be smaller than the original A-side locations.  Hence, we must
                // subtract the (negative) delta, thus increasing the B-side location.
                final int newStart1 = e.getValue() - delta;
                e.setValue(newStart1);
            }
        }

        @Override
        public int getASideStartLocationOfAppearedFunction(Method f) {
            Integer result = appearedFunctionASideStartLocation.get(f);
            if (result == null) {
                LOG.warn("Illegal argument: " + f);
                return f.start1;
            }
            return result;
        }
    }

    private class AppearedAndDisappearedFunctionHandlerFactory {
        final ChangeId changeId;
        final DiffASideFunctionList aSideFunctionList;
        final DiffBSideFunctionList bSideFunctionList;

        public AppearedAndDisappearedFunctionHandlerFactory(ChangeId changeId, DiffASideFunctionList aSideFunctionList, DiffBSideFunctionList bSideFunctionList) {
            this.changeId = changeId;
            this.aSideFunctionList = aSideFunctionList;
            this.bSideFunctionList = bSideFunctionList;
        }

        public AppearedAndDisappearedFunctionHandler getInstance() {
            final AppearedAndDisappearedFunctionHandler result;
            if (!aSideFunctionList.getPath().equals(bSideFunctionList.getPath())) {
                result = new NullAppearedAndDisappearedFunctionHandler(changeId, aSideFunctionList, bSideFunctionList);
            } else {
                result = new SimpleAppearedAndDisappearedFunctionHandler(changeId, aSideFunctionList, bSideFunctionList);
            }
            result.initialize();
            return result;
        }
    }

    private final ChangeId changeId;
    private final Consumer<FunctionChangeHunk> changedFunctionConsumer;

    private final AppearedAndDisappearedFunctionHandler appearedAndDisappearedFunctionHandler;
    /**
     * Number of the change hunk within the file for which this mapper has been created.  It is increased each time
     * {@link #accept(Edit)} is called.
     */
    int numHunkInFile = 0;
    private Map<Method, List<FunctionChangeHunk>> hunksForCurrentEdit;
    private List<FunctionChangeHunk> possibleSignatureChangesASides;
    private List<FunctionChangeHunk> possibleSignatureChangesBSides;
    private Map<Method, Method> movedMethodsDueToPathDifferences;

    public CommitHunkToFunctionLocationMapper(ChangeId changeId,
                                              DiffASideFunctionList functionsInOldPath,
                                              DiffBSideFunctionList functionsInNewPath,
                                              Map<Method, Method> movedMethodsDueToPathDifferences,
                                              Consumer<FunctionChangeHunk> changedFunctionConsumer) {
        this.changeId = changeId;
        this.oldPath = functionsInOldPath.getPath();
        this.newPath = functionsInNewPath.getPath();
        this.functionsInOldPathByOccurrence = new LinkedList<>(functionsInOldPath.getFunctionsByOccurrence());
        this.functionsInNewPathByOccurrence = new LinkedList<>(functionsInNewPath.getFunctionsByOccurrence());
        this.movedMethodsDueToPathDifferences = movedMethodsDueToPathDifferences;
        this.changedFunctionConsumer = changedFunctionConsumer;

        this.appearedAndDisappearedFunctionHandler = new AppearedAndDisappearedFunctionHandlerFactory(changeId, functionsInOldPath, functionsInNewPath).getInstance();
        this.appearedAndDisappearedFunctionHandler.initialize();
    }

    @Override
    public void accept(Edit edit) {
        // NOTE, 2016-12-09, wf: To know which *existing* functions have been modified, we
        // only need to look at the "A"-side of the edit and can ignore the "B" side.
        // This is good because "A"-side line numbers are much easier to correlate with the
        // function locations we have than the "B"-side offsets.
        hunksForCurrentEdit = new HashMap<>();
        possibleSignatureChangesASides = new ArrayList<>();
        possibleSignatureChangesBSides = new ArrayList<>();
        try {
            analyzeASide(edit);
            analyzeBSide(edit);
            appearedAndDisappearedFunctionHandler.adjustAppearedFunctionASideStartLocations(edit);
            publishHunksForCurrentEdit();
        } finally {
            numHunkInFile++;
            hunksForCurrentEdit = null;
            possibleSignatureChangesASides = null;
            possibleSignatureChangesBSides = null;
        }
    }

    public void handleUntreatedAddedAndDeletedFunctions() {
        for (Method f : appearedAndDisappearedFunctionHandler.getUntreatedDisappearedFunctions()) {
            LOG.debug("Commit " + changeId + " deletes function: " + f + " but change has not been published. Publishing now.");
            FunctionChangeHunk hunk = FunctionChangeHunk.makePseudoDel(changeId, oldPath, newPath, f);
            changedFunctionConsumer.accept(hunk);
        }

        for (Method f : appearedAndDisappearedFunctionHandler.getUntreatedAppearedFunctions()) {
            LOG.debug("Commit " + changeId + "    adds function: " + f + " but change has not been published. Publishing now.");
            int aSideStartLocation = appearedAndDisappearedFunctionHandler.getASideStartLocationOfAppearedFunction(f);
            FunctionChangeHunk hunk = FunctionChangeHunk.makePseudoAdd(changeId, oldPath, newPath, f, Optional.of(aSideStartLocation));
            changedFunctionConsumer.accept(hunk);
        }
    }

    private void publishHunksForCurrentEdit() {
        processPossibleSignatureChanges();
        for (Map.Entry<Method, Method> e : movedMethodsDueToPathDifferences.entrySet()) {
            remapModsOfRenamedFunction(e.getKey(), e.getValue(), Optional.empty());
        }

        for (List<FunctionChangeHunk> hunks : hunksForCurrentEdit.values()) {
            switch (hunks.size()) {
                case 0: /* Can this even happen? */
                    break;
                case 1: /* Expected case */
                    publishSingleHunk(hunks.get(0));
                    break;
                default: /* May also happen if a delete and an add are right next to each other */
                    LOG.debug("Merging two or more hunks");
                    mergeAndPublishAAndBSideHunks(hunks);
            }
        }
    }

    private void publishSingleHunk(FunctionChangeHunk hunk) {
        //LOG.debug("Single hunk case");
        markAddedAndDeletedFunctionsAsTreated(hunk);
        changedFunctionConsumer.accept(hunk);
    }

    private void markAddedAndDeletedFunctionsAsTreated(FunctionChangeHunk hunk) {
        Method addFunction = null;
        Method delFunction = null;
        switch (hunk.getModType()) {
            case ADD:
                addFunction = hunk.getFunction();
                break;
            case DEL:
                delFunction = hunk.getFunction();
                break;
            case MOVE:
                delFunction = hunk.getFunction();
                addFunction = hunk.getNewFunction().get();
                break;
        }

        appearedAndDisappearedFunctionHandler.markFunctionAsDeleted(delFunction);
        appearedAndDisappearedFunctionHandler.markFunctionAsAdded(addFunction);
    }

    private void processPossibleSignatureChanges() {
        if (!haveExactlyOneSignatureChangeOnASideAndBSide()) return;
        FunctionChangeHunk del = possibleSignatureChangesASides.get(0);
        FunctionChangeHunk add = possibleSignatureChangesBSides.get(0);

        ChangeHunk delChangeHunk = del.getHunk();
        ChangeHunk addChangeHunk = add.getHunk();

        final Method delFunction = del.getFunction();
        final Method addFunction = add.getFunction();


        // Test the (unlikely) case that the signature didn't change
        final boolean logIsDebug = LOG.isDebugEnabled();

        if (delFunction.equals(addFunction)) { // same signature, same file
            if (logIsDebug) {
                LOG.debug("Suspected signature change was a false alarm: " + del + " -> " + add);
            }
            forgetHunk(del);
            forgetHunk(add);
            ChangeHunk mergedChangeHunk = mergeDelAndAddChangeHunk(delChangeHunk, addChangeHunk);
            rememberHunk(delFunction, FunctionChangeHunk.ModificationType.MOD, mergedChangeHunk);
        } else if (isPlausibleRename(del, add)) {
            // Signature did change!
            if (logIsDebug) {
                LOG.debug("Detected function signature change: " + del + " -> " + add);
            }
            forgetHunk(del);
            forgetHunk(add);

            ChangeHunk mergedChangeHunk = mergeDelAndAddChangeHunk(delChangeHunk, addChangeHunk);
            FunctionChangeHunk move = new FunctionChangeHunk(delFunction, mergedChangeHunk,
                    FunctionChangeHunk.ModificationType.MOVE, addFunction);
            appearedAndDisappearedFunctionHandler.markFunctionAsDeleted(delFunction);
            appearedAndDisappearedFunctionHandler.markFunctionAsAdded(addFunction);

            remapModsOfRenamedFunction(delFunction, addFunction, Optional.of(mergedChangeHunk.getHunkNo()));
            changedFunctionConsumer.accept(move);
        } else {
            LOG.info("Treating signature change as separate del and add since the difference is too great: " + del + " -> " + add);
        }
    }

    private static boolean isPlausibleRename(FunctionChangeHunk del, FunctionChangeHunk add) {
        Method delFunction = del.getFunction();
        Method addFunction = add.getFunction();

        return isPlausibleRename(delFunction, addFunction);
    }

    private static boolean isPlausibleRename(Method delFunction, Method addFunction) {
        if (delFunction.hasSameOriginalSignature(addFunction)) {
            return true;
        }

        if (delFunction.hasSameName(addFunction)) {
            return true;
        }

        return isPlausibleRename(delFunction.functionName, addFunction.functionName);
    }

    private static boolean isPlausibleRename(String oldFunctionName, String newFunctionName) {
        int threshold = (Math.min(oldFunctionName.length(), newFunctionName.length()) * 3) / 4;
        threshold = Math.max(threshold, 1);
        LevenshteinDistance distMeasure = new LevenshteinDistance();
        // If initialized with a threshold, apply will return -1 if the threshold is exceeded.
        int dist = distMeasure.apply(oldFunctionName, newFunctionName);
        if (dist < threshold) {
            LOG.info("Function names are similar enough for a rename: " + oldFunctionName + " -> " + newFunctionName +
                    " threshold=" + threshold + " actual distance=" + dist);
            return true;
        } else {
            LOG.info("Function names are too dissimilar for a rename: " + oldFunctionName + " -> " + newFunctionName +
                    " threshold=" + threshold + " actual distance=" + dist);
        }

//        String delSignature = delFunction.functionSignatureXml;
//        String addSignature = addFunction.functionSignatureXml;
//
//        threshold = (Math.min(delSignature.length(), addSignature.length()) * 4) / 5;
//        threshold = Math.max(threshold, 1);
//        distMeasure = new LevenshteinDistance();
//        // If initialized with a threshold, apply will return -1 if the threshold is exceeded.
//        dist = distMeasure.apply(delSignature, addSignature);
//        if (dist < threshold) {
//            LOG.debug("Function signatures are similar enough for a rename: " + delSignature + " -> " + addSignature);
//            return true;
//        } else {
//            LOG.debug("Function signatures are too dissimilar enough for a rename: " + delSignature + " -> " + addSignature);
//        }

        return false;
    }

    private ChangeHunk mergeDelAndAddChangeHunk(ChangeHunk delChangeHunk, ChangeHunk addChangeHunk) {
        return new ChangeHunk(delChangeHunk,
                delChangeHunk.getLinesDeleted() + addChangeHunk.getLinesDeleted(),
                delChangeHunk.getLinesAdded() + addChangeHunk.getLinesAdded()
        );
    }

    private void remapModsOfRenamedFunction(Method delFunction, Method addFunction, Optional<Integer> moveHunkNo) {
        // Remap all MODs to the old function so they become MODs of the new function
        List<FunctionChangeHunk> hunksForDelFunction = hunksForCurrentEdit.get(delFunction);
        List<FunctionChangeHunk> hunksForAddFunction = hunksForCurrentEdit.get(addFunction);
        if ((hunksForDelFunction == null) && (hunksForAddFunction == null)) {
            return;
        }

        int numRemappedMods = 0;

        if (hunksForDelFunction != null) {
            hunksForDelFunction = new ArrayList<>(hunksForDelFunction);
            for (FunctionChangeHunk fHunk : hunksForDelFunction) {
                if (fHunk.getModType() != FunctionChangeHunk.ModificationType.MOD) continue;
                if (moveHunkNo.isPresent()) {
                    if (fHunk.getHunk().getHunkNo() <= moveHunkNo.get()) continue;
                }
                forgetHunk(fHunk);
                rememberHunk(addFunction, FunctionChangeHunk.ModificationType.MOD, fHunk.getHunk());
                numRemappedMods++;
            }
        }

        if (hunksForAddFunction != null) {
            hunksForAddFunction = new ArrayList<>(hunksForAddFunction);
            for (FunctionChangeHunk fHunk : hunksForAddFunction) {
                if (fHunk.getModType() != FunctionChangeHunk.ModificationType.MOD) continue;
                if (moveHunkNo.isPresent()) {
                    if (fHunk.getHunk().getHunkNo() >= moveHunkNo.get()) continue;
                }
                forgetHunk(fHunk);
                rememberHunk(delFunction, FunctionChangeHunk.ModificationType.MOD, fHunk.getHunk());
                numRemappedMods++;
            }
        }

        if (LOG.isDebugEnabled() && (numRemappedMods > 0)) {
            LOG.debug("Remapped " + numRemappedMods + " modification after function signature change: "
                    + delFunction + " -> " + addFunction);
        }
    }

    private boolean haveExactlyOneSignatureChangeOnASideAndBSide() {
        int numChanges = possibleSignatureChangesASides.size();
        if (numChanges != possibleSignatureChangesBSides.size()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Possible signature changes on A side don't match possible signature changes on B side. Aborting. A side changes: "
                        + possibleSignatureChangesASides + " B side changes: " + possibleSignatureChangesBSides);
            }
            return false;
        } else if (numChanges == 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No signature change detected for commit " + changeId + " edit " + numHunkInFile);
            }
            return false;
        } else if (numChanges > 1) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Don't know how to deal with multiple signature changes in one edit. Aborting. A side changes: "
                        + possibleSignatureChangesASides + " B side changes: " + possibleSignatureChangesBSides);
            }
            return false;
        }
        return true;
    }

    /**
     * Merge a non-empty list of changes to the same function and publish them. This is meant to merge edits that both
     * delete some lines and add some lines in the same place, i.e. edits with modifications on both A and B side.
     */
    private void mergeAndPublishAAndBSideHunks(List<FunctionChangeHunk> hunks) {
        int linesAdded = 0;
        int linesDeleted = 0;

        for (FunctionChangeHunk fh : hunks) {
            if (fh.getModType() != FunctionChangeHunk.ModificationType.MOD) {
                markAddedAndDeletedFunctionsAsTreated(fh);
                changedFunctionConsumer.accept(fh);
                continue;
            }
            ChangeHunk h = fh.getHunk();
            linesAdded += h.getLinesAdded();
            linesDeleted += h.getLinesDeleted();
        }

        final FunctionChangeHunk functionHunk = hunks.get(0);
        final ChangeHunk aggregatedChangeHunk = new ChangeHunk(functionHunk.getHunk(), linesDeleted, linesAdded);
        FunctionChangeHunk merged = new FunctionChangeHunk(functionHunk.getFunction(), aggregatedChangeHunk,
                FunctionChangeHunk.ModificationType.MOD);
        changedFunctionConsumer.accept(merged);
    }

    private void analyzeASide(Edit edit) {
        LOG.debug("Analyzing A-side of commit " + changeId);

        final int remBegin = edit.getBeginA();
        final int remEnd = edit.getEndA();

        final boolean logDebug = LOG.isDebugEnabled();

        for (Iterator<Method> fIter = functionsInOldPathByOccurrence.iterator(); fIter.hasNext(); ) {
            Method f = fIter.next();
            if (logDebug) {
                LOG.debug("Checking " + f);
            }

            if (editOverlaps(f, remBegin, remEnd)) {
                if (editCompletelyCovers(f, remBegin, remEnd)) {
                    if (logDebug) {
                        LOG.debug("Edit " + remBegin + ".." + remEnd + " fully deletes " + f);
                    }
                    markFunctionASideEdit(edit, f, FunctionChangeHunk.ModificationType.DEL, false);
                } else {
                    if (logDebug) {
                        LOG.debug("Edit " + remBegin + ".." + remEnd + " deletes lines from " + f);
                    }

                    if (editOverlapsSignature(f, remBegin, remEnd) && appearedAndDisappearedFunctionHandler.isDisappearedFunction(f)) {
                        if (logDebug) {
                            LOG.debug("Commit " + changeId + " might delete " + f);
                        }
                        markFunctionASideEdit(edit, f, FunctionChangeHunk.ModificationType.DEL, true);
                    } else {
                        markFunctionASideEdit(edit, f, FunctionChangeHunk.ModificationType.MOD, false);
                    }
                }
            } else if (f.end1 < remBegin) {
                if (logDebug) {
                    LOG.debug("No future edits possible for " + f);
                }
                fIter.remove();
            } else if (f.start1 > remEnd) {
                LOG.debug("Suspending search for modified functions at " + f);
                break;
            }
        }
    }

    private void analyzeBSide(Edit edit) {
        LOG.debug("Analyzing B-side of commit " + changeId);

        final int addBegin = edit.getBeginB();
        final int addEnd = edit.getEndB();

        final boolean logDebug = LOG.isDebugEnabled();

        for (Iterator<Method> fIter = functionsInNewPathByOccurrence.iterator(); fIter.hasNext(); ) {
            Method f = fIter.next();
            if (logDebug) {
                LOG.debug("Checking " + f);
            }
            if (editOverlaps(f, addBegin, addEnd)) {
                if (editCompletelyCovers(f, addBegin, addEnd)) {
                    if (logDebug) {
                        LOG.debug("Edit " + addBegin + ".." + addEnd + " fully adds " + f);
                    }
                    markFunctionBSideEdit(edit, f, FunctionChangeHunk.ModificationType.ADD, false);
                } else {
                    if (logDebug) {
                        LOG.debug("Edit " + addBegin + ".." + addEnd + " adds lines to " + f);
                    }

                    if (editOverlapsSignature(f, addBegin, addEnd) && appearedAndDisappearedFunctionHandler.isAppearedFunction(f)) {
                        if (logDebug) {
                            LOG.debug("Commit " + changeId + " might add    " + f);
                        }
                        markFunctionBSideEdit(edit, f, FunctionChangeHunk.ModificationType.ADD, true);
                    } else {
                        markFunctionBSideEdit(edit, f, FunctionChangeHunk.ModificationType.MOD, false);
                    }
                }
            } else if (f.end1 < addBegin) {
                if (logDebug) {
                    LOG.debug("No future edits possible for " + f);
                }
                fIter.remove();
            } else if (f.start1 > addEnd) {
                LOG.debug("Suspending search for modified functions at " + f);
                break;
            }
        }
    }

    private void markFunctionASideEdit(Edit edit, Method f, FunctionChangeHunk.ModificationType modType,
                                       boolean possibleSignatureChange) {
        ChangeHunk hunk = hunkFromASideEdit(f, edit);
        Optional<FunctionChangeHunk> fHunk = rememberHunk(f, modType, hunk);
        if (possibleSignatureChange && fHunk.isPresent()) {
            possibleSignatureChangesASides.add(fHunk.get());
        }
    }

    private void markFunctionBSideEdit(Edit edit, Method f, FunctionChangeHunk.ModificationType modType,
                                       boolean possibleSignatureChange) {
        ChangeHunk hunk = hunkFromBSideEdit(f, edit);
        Optional<FunctionChangeHunk> fHunk = rememberHunk(f, modType, hunk);
        if (possibleSignatureChange && fHunk.isPresent()) {
            possibleSignatureChangesBSides.add(fHunk.get());
        }
    }

    private Optional<FunctionChangeHunk> rememberHunk(Method f, FunctionChangeHunk.ModificationType modType, ChangeHunk hunk) {
        if ((hunk.getLinesAdded() == 0) && (hunk.getLinesDeleted() == 0)
                && (modType == FunctionChangeHunk.ModificationType.MOD)) {
            LOG.debug("Ignoring hunk.  No lines were added or removed in " + f);
            return Optional.empty();
        }
        FunctionChangeHunk fHunk = new FunctionChangeHunk(f, hunk, modType);
        List<FunctionChangeHunk> hunksForFunction = hunksForCurrentEdit.get(f);
        if (hunksForFunction == null) {
            hunksForFunction = new LinkedList<>();
            hunksForCurrentEdit.put(f, hunksForFunction);
        }
        hunksForFunction.add(fHunk);
        //logEdit(f, edit);
        return Optional.of(fHunk);
    }

    private boolean forgetHunk(FunctionChangeHunk fh) {
        final Method function = fh.getFunction();
        List<FunctionChangeHunk> changesToSameFunction = hunksForCurrentEdit.get(function);
        if (changesToSameFunction == null) return false;
        boolean result = changesToSameFunction.remove(fh);
        if (changesToSameFunction.isEmpty()) {
            hunksForCurrentEdit.remove(function);
        }
        return result;
    }

    private ChangeHunk hunkFromASideEdit(Method f, Edit edit) {
        final int fBegin = f.start1 - 1;
        final int fEnd = f.end1 - 1;
        final int remBegin = edit.getBeginA();
        final int remEnd = edit.getEndA();
        final int linesDeleted;

        if (remBegin <= fBegin) {
            if (remEnd > fEnd) {
                // Edit deletes the whole function
                linesDeleted = Math.min(f.getGrossLoc(), edit.getLengthA());
                //LOG.debug("hunkFromASideEdit: 1 " + linesDeleted);
            } else {
                // Edit starts before the function, and ends within it.
                linesDeleted = remEnd - fBegin;
                //LOG.debug("hunkFromASideEdit: 2 " + linesDeleted);
            }
        } else { // remBegin > fBegin // Edit starts within the function
            if (remEnd > fEnd) {
                // Edit starts within the function, and ends after it.
                linesDeleted = fEnd - remBegin;
                //LOG.debug("hunkFromASideEdit: 3 " + linesDeleted);
            } else { // remEnd < fEnd
                // Edit is fully contained within the function.
                linesDeleted = edit.getLengthA();
                //LOG.debug("hunkFromASideEdit: 4 " + linesDeleted);
            }
        }
        return new ChangeHunk(changeId, oldPath, newPath, this.numHunkInFile, linesDeleted, 0);
    }

    private ChangeHunk hunkFromBSideEdit(Method f, Edit edit) {
        final int fBegin = f.start1 - 1;
        final int fEnd = f.end1 - 1;
        final int addBegin = edit.getBeginB();
        final int addEnd = edit.getEndB();
        final int linesAdded;

        if (addBegin <= fBegin) {
            if (addEnd > fEnd) {
                // Edit adds the whole function
                linesAdded = Math.min(f.getGrossLoc(), edit.getLengthB());
            } else {
                // Edit starts before the function, and ends within it.
                linesAdded = addEnd - fBegin;
            }
        } else { // addBegin > fBegin // Edit starts within the function
            if (addEnd > fEnd) {
                // Edit starts within the function, and ends after it.
                linesAdded = fEnd - addBegin;
            } else { // addEnd <= fEnd
                // Edit is fully contained within the function.
                linesAdded = edit.getLengthB();
            }
        }
        return new ChangeHunk(changeId, oldPath, newPath, this.numHunkInFile, 0, linesAdded);
    }

    /*
    private void logEdit(Method f, Edit edit) {
        if (LOG.isDebugEnabled() || LOG.isTraceEnabled()) {
            int editBegin = edit.getBeginA();
            int editEnd = edit.getEndA();
            LOG.debug("Detected edit to " + f + ": " + editBegin + "," + editEnd);
            String[] lines = f.getSourceCode().split("\n");
            int fBegin = (f.start1 - 1);
            int adjustedBegin = Math.max(editBegin - fBegin, 0);
            int adjustedEnd = Math.min(editEnd - fBegin, lines.length);
            for (int iLine = adjustedBegin; iLine < adjustedEnd; iLine++) {
                LOG.debug("- " + lines[iLine]);
            }
            LOG.trace(f.sourceCodeWithLineNumbers());
        }
    }
    */

    private boolean editOverlaps(Method func, final int editBegin, final int editEnd) {
        // NOTE, 2017-02-04, wf: We subtract 1 from the function's line
        // numbers because function line numbers are 1-based, whereas edit
        // line numbers are 0-based.

        // NOTE, 2018-03-15, wf: The end of an edit is actually the first
        // line *not* edited.  Thus, we need to compare the end with '>',
        // not '>='.
        int fBegin = func.start1 - 1;
        int fEnd = func.end1 - 1;
        return ((editBegin < fEnd) && (editEnd > fBegin));
    }

    /**
     * Determine whether the given edit touches the function's signature.
     *
     * @param func
     * @param editBegin
     * @param editEnd
     * @return <code>true</code> iff the signature is probably changed by the commit
     */
    private boolean editOverlapsSignature(Method func, final int editBegin, final int editEnd) {
        // NOTE, 2017-03-28, wf: We subtract 1 from the function's line
        // numbers because function line numbers are 1-based, whereas edit
        // line numbers are 0-based.

        // NOTE, 2018-03-15, wf: The end of an edit is actually the first
        // line *not* edited.  Thus, we need to compare the end with '>',
        // not '>='.
        int signatureBegin = func.start1 - 1;
        int signatureEnd = signatureBegin + func.getSignatureGrossLinesOfCode();
        return ((editBegin < signatureEnd) && (editEnd > signatureBegin));
    }

    private boolean editCompletelyCovers(Method func, final int editBegin, final int editEnd) {
        // NOTE, 2017-02-04, wf: We subtract 1 from the function's line
        // numbers because function line numbers are 1-based, whereas edit
        // line numbers are 0-based.

        // NOTE, 2018-11-11, wf: For reasons I don't understand, edits on the
        // A side that should exactly cover a function sometimes start one
        // line before a function start and sometimes end one line after the
        // function end (but never both).  On the B side, the edit begin always
        // matches exactly with our locations, but the end is
        // consistently one line after the end of the function.  Hence, we use
        // <= and >= to compare, although it is not 100% correct.

        //  See e.g. the commit f214bb2115994cc6b4123f3d06db0452f17f2e99
        // in OpenVPN and the changes to base64.c
        int fBegin = func.start1 - 1;
        int fEnd = func.end1 - 1;
        return ((editBegin <= fBegin) && (editEnd >= fEnd));
//
//        if (result) {
//            LOG.debug("Delete/add detected. Edit end: " + editEnd + "; function end: " + fEnd);
//        }
//
//        return result;
    }
}
