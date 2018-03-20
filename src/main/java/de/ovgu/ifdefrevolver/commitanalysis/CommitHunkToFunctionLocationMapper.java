package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import org.apache.log4j.Logger;
import org.eclipse.jgit.diff.Edit;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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


    private final String commitId;
    private final Consumer<FunctionChangeHunk> changedFunctionConsumer;
    /**
     * Number of the change hunk within the file for which this mapper has been created.  It is increased each time
     * {@link #accept(Edit)} is called.
     */
    int numHunkInFile = 0;

    public CommitHunkToFunctionLocationMapper(String commitId,
                                              String oldPath, Collection<Method> functionsInOldPathByOccurrence,
                                              String newPath, Collection<Method> functionsInNewPathByOccurrence,
                                              Consumer<FunctionChangeHunk> changedFunctionConsumer) {
        this.commitId = commitId;
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.functionsInOldPathByOccurrence = new LinkedList<>(functionsInOldPathByOccurrence);
        this.functionsInNewPathByOccurrence = new LinkedList<>(functionsInNewPathByOccurrence);
        this.changedFunctionConsumer = changedFunctionConsumer;
    }

    @Override
    public void accept(Edit edit) {
        // NOTE, 2016-12-09, wf: To know which *existing* functions have been modified, we
        // only need to look at the "A"-side of the edit and can ignore the "B" side.
        // This is good because "A"-side line numbers are much easier to correlate with the
        // function locations we have than the "B"-side offsets.
        try {
            analyzeASide(edit);
            analyzeBSide(edit);
        } finally {
            numHunkInFile++;
        }
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
                final FunctionChangeHunk.ModificationType modType;
                if (editCompletelyCovers(f, remBegin, remEnd)) {
                    modType = FunctionChangeHunk.ModificationType.DEL;
                    if (logDebug) {
                        LOG.debug("Edit " + remBegin + ".." + remEnd + " fully deletes " + f);
                    }
                } else {
                    modType = FunctionChangeHunk.ModificationType.MOD;
                    if (logDebug) {
                        LOG.debug("Edit " + remBegin + ".." + remEnd + " deletes lines from " + f);
                    }
                }
                markFunctionASideEdit(edit, f, modType);
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
                final FunctionChangeHunk.ModificationType modType;
                if (editCompletelyCovers(f, addBegin, addEnd)) {
                    modType = FunctionChangeHunk.ModificationType.ADD;
                    if (logDebug) {
                        LOG.debug("Edit " + addBegin + ".." + addEnd + " fully adds " + f);
                    }
                } else {
                    modType = FunctionChangeHunk.ModificationType.MOD;
                    if (logDebug) {
                        LOG.debug("Edit " + addBegin + ".." + addEnd + " adds lines to " + f);
                    }
                }
                markFunctionBSideEdit(edit, f, modType);
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

    private void markFunctionASideEdit(Edit edit, Method f, FunctionChangeHunk.ModificationType modType) {
        ChangeHunk hunk = hunkFromASideEdit(f, edit);
        publishHunk(f, modType, hunk);
    }

    private void markFunctionBSideEdit(Edit edit, Method f, FunctionChangeHunk.ModificationType modType) {
        ChangeHunk hunk = hunkFromBSideEdit(f, edit);
        publishHunk(f, modType, hunk);
    }

    private void publishHunk(Method f, FunctionChangeHunk.ModificationType modType, ChangeHunk hunk) {
        if ((hunk.getLinesAdded() == 0) && (hunk.getLinesDeleted() == 0)) {
            LOG.debug("Ignoring hunk.  No lines were added or removed in " + f);
            return;
        }
        FunctionChangeHunk fHunk = new FunctionChangeHunk(f, hunk, modType);
        changedFunctionConsumer.accept(fHunk);
        //logEdit(f, edit);
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
                LOG.debug("hunkFromASideEdit: 1 " + linesDeleted);
            } else {
                // Edit starts before the function, and ends within it.
                linesDeleted = remEnd - fBegin;
                LOG.debug("hunkFromASideEdit: 2 " + linesDeleted);
            }
        } else { // remBegin > fBegin // Edit starts within the function
            if (remEnd > fEnd) {
                // Edit starts within the function, and ends after it.
                linesDeleted = fEnd - remBegin;
                LOG.debug("hunkFromASideEdit: 3 " + linesDeleted);
            } else { // remEnd < fEnd
                // Edit is fully contained within the function.
                linesDeleted = edit.getLengthA();
                LOG.debug("hunkFromASideEdit: 4 " + linesDeleted);
            }
        }
        return new ChangeHunk(commitId, oldPath, this.numHunkInFile, linesDeleted, 0);
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
        return new ChangeHunk(commitId, newPath, this.numHunkInFile, 0, linesAdded);
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
