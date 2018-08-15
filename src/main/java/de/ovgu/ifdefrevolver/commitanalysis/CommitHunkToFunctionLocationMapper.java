package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
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

    final Set<Method> appearedFunctions;
    final Set<Method> disappearedFunctions;
    final Set<Method> untreatedAppearedFunctions;
    final Set<Method> untreatedDisappearedFunctions;

    private final String commitId;
    private final Consumer<FunctionChangeHunk> changedFunctionConsumer;
    /**
     * Number of the change hunk within the file for which this mapper has been created.  It is increased each time
     * {@link #accept(Edit)} is called.
     */
    int numHunkInFile = 0;
    private Map<Method, List<FunctionChangeHunk>> hunksForCurrentEdit;
    private List<FunctionChangeHunk> possibleSignatureChangesASides;
    private List<FunctionChangeHunk> possibleSignatureChangesBSides;
    private Map<Method, Method> movedMethodsDueToPathDifferences;

    public CommitHunkToFunctionLocationMapper(String commitId,
                                              String oldPath, Collection<Method> functionsInOldPathByOccurrence,
                                              String newPath, Collection<Method> functionsInNewPathByOccurrence,
                                              Map<Method, Method> movedMethodsDueToPathDifferences,
                                              Consumer<FunctionChangeHunk> changedFunctionConsumer) {
        this.commitId = commitId;
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.functionsInOldPathByOccurrence = new LinkedList<>(functionsInOldPathByOccurrence);
        this.functionsInNewPathByOccurrence = new LinkedList<>(functionsInNewPathByOccurrence);
        this.movedMethodsDueToPathDifferences = movedMethodsDueToPathDifferences;
        this.changedFunctionConsumer = changedFunctionConsumer;

        if (oldPath.equals(newPath)) {
            appearedFunctions = new LinkedHashSet<>(functionsInNewPathByOccurrence);
            appearedFunctions.removeAll(functionsInOldPathByOccurrence);
            disappearedFunctions = new LinkedHashSet<>(functionsInOldPathByOccurrence);
            disappearedFunctions.removeAll(functionsInNewPathByOccurrence);
            if (LOG.isDebugEnabled()) {
                for (Method m : appearedFunctions) {
                    LOG.debug("Commit " + commitId + "    adds function: " + m);
                }
                for (Method m : disappearedFunctions) {
                    LOG.debug("Commit " + commitId + " deletes function: " + m);
                }
            }
        } else {
            appearedFunctions = Collections.emptySet();
            disappearedFunctions = Collections.emptySet();
        }

        untreatedAppearedFunctions = new HashSet<>(appearedFunctions);
        untreatedDisappearedFunctions = new HashSet<>(disappearedFunctions);
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
            publishHunksForCurrentEdit();
        } finally {
            numHunkInFile++;
            hunksForCurrentEdit = null;
            possibleSignatureChangesASides = null;
            possibleSignatureChangesBSides = null;
        }
    }

    public void handleUntreatedAddedAndDeletedFunctions() {
        for (Method f : untreatedDisappearedFunctions) {
            LOG.info("Commit " + commitId + " deletes function: " + f + " but change has not been published!");
            FunctionChangeHunk hunk = FunctionChangeHunk.makePseudoDel(commitId, oldPath, newPath, f);
            changedFunctionConsumer.accept(hunk);
        }

        for (Method f : untreatedAppearedFunctions) {
            LOG.info("Commit " + commitId + "    adds function: " + f + " but change has not been published!");
            FunctionChangeHunk hunk = FunctionChangeHunk.makePseudoAdd(commitId, oldPath, newPath, f);
            changedFunctionConsumer.accept(hunk);
        }
    }

    private void publishHunksForCurrentEdit() {
        processPossibleSignatureChanges();
        for (Map.Entry<Method, Method> e : movedMethodsDueToPathDifferences.entrySet()) {
            remapModsOfRenamedFunction(e.getKey(), e.getValue());
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

        untreatedDisappearedFunctions.remove(delFunction);
        untreatedAppearedFunctions.remove(addFunction);
    }

    private void processPossibleSignatureChanges() {
        if (!haveExactlyOneSignatureChangeOnASideAndBSide()) return;
        FunctionChangeHunk del = possibleSignatureChangesASides.get(0);
        FunctionChangeHunk add = possibleSignatureChangesBSides.get(0);

        ChangeHunk delChangeHunk = del.getHunk();
        ChangeHunk addChangeHunk = add.getHunk();

        ChangeHunk mergedChangeHunk = new ChangeHunk(delChangeHunk,
                delChangeHunk.getLinesDeleted() + addChangeHunk.getLinesDeleted(),
                delChangeHunk.getLinesAdded() + addChangeHunk.getLinesAdded()
        );

        final Method delFunction = del.getFunction();
        final Method addFunction = add.getFunction();


        // Test the (unlikely) case that the signature didn't change
        if (delFunction.equals(addFunction)) { // same signature, same file
            if (LOG.isDebugEnabled()) {
                LOG.debug("Suspected signature change was a false alarm: " + del + " -> " + add);
            }
            forgetHunk(del);
            forgetHunk(add);
            rememberHunk(delFunction, FunctionChangeHunk.ModificationType.MOD, mergedChangeHunk);
        } else {
            // Signature did change!
            if (LOG.isDebugEnabled()) {
                LOG.debug("Detected function signature change: " + del + " -> " + add);
            }
            forgetHunk(del);
            forgetHunk(add);
            FunctionChangeHunk move = new FunctionChangeHunk(delFunction, mergedChangeHunk,
                    FunctionChangeHunk.ModificationType.MOVE, addFunction);
            changedFunctionConsumer.accept(move);
            untreatedDisappearedFunctions.remove(delFunction);
            untreatedAppearedFunctions.remove(addFunction);
            remapModsOfRenamedFunction(delFunction, addFunction);
        }
    }

    private void remapModsOfRenamedFunction(Method delFunction, Method addFunction) {
        // Remap all MODs to the old function so they become MODs of the new function
        List<FunctionChangeHunk> hunksForDelFunction = hunksForCurrentEdit.get(delFunction);
        if ((hunksForDelFunction == null) || hunksForDelFunction.isEmpty()) {
            return;
        }

        hunksForDelFunction = new ArrayList<>(hunksForDelFunction);
        int numRemappedMods = 0;
        for (FunctionChangeHunk fHunk : hunksForDelFunction) {
            if (fHunk.getModType() != FunctionChangeHunk.ModificationType.MOD) continue;
            forgetHunk(fHunk);
            rememberHunk(addFunction, FunctionChangeHunk.ModificationType.MOD, fHunk.getHunk());
            numRemappedMods++;
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
                LOG.debug("No signature change detected for commit " + commitId
                        + " edit " + numHunkInFile);
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
        LOG.debug("Analyzing A-side of commit " + commitId);

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

                    if (editOverlapsSignature(f, remBegin, remEnd) && disappearedFunctions.contains(f)) {
                        if (logDebug) {
                            LOG.debug("Commit " + commitId + " might delete " + f);
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
        LOG.debug("Analyzing B-side of commit " + commitId);

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

                    if (editOverlapsSignature(f, addBegin, addEnd) && appearedFunctions.contains(f)) {
                        if (logDebug) {
                            LOG.debug("Commit " + commitId + " might add    " + f);
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
        return new ChangeHunk(commitId, oldPath, newPath, this.numHunkInFile, linesDeleted, 0);
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
        return new ChangeHunk(commitId, oldPath, newPath, this.numHunkInFile, 0, linesAdded);
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

        // NOTE, 2018-03-15, wf: The end of an edit is actually the first
        // line *not* edited.  Thus, we need to compare the end with '>',
        // not '>='.
        int fBegin = func.start1 - 1;
        int fEnd = func.end1 - 1;
        return ((editBegin <= fBegin) && (editEnd > fEnd));
//
//        if (result) {
//            LOG.debug("Delete/add detected. Edit end: " + editEnd + "; function end: " + fEnd);
//        }
//
//        return result;
    }
}
