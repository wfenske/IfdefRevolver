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

    final List<Method> functionsInOldPathByOccurrence;
    /**
     * A-side file path of the file being modified
     */
    final String oldPath;
    private final String commitId;
    private final Consumer<FunctionChangeHunk> changedFunctionConsumer;
    /**
     * Number of the change hunk within the file for which this mapper has been created.  It is increased each time
     * {@link #accept(Edit)} is called.
     */
    int numHunkInFile = 0;

    public CommitHunkToFunctionLocationMapper(String commitId, String oldPath, Collection<Method> functionsInOldPathByOccurrence, Consumer<FunctionChangeHunk> changedFunctionConsumer) {
        this.commitId = commitId;
        this.oldPath = oldPath;
        this.functionsInOldPathByOccurrence = new LinkedList<>(functionsInOldPathByOccurrence);
        this.changedFunctionConsumer = changedFunctionConsumer;
    }

    @Override
    public void accept(Edit edit) {
        // NOTE, 2016-12-09, wf: To know which *existing* functions have been modified, we
        // only need to look at the "A"-side of the edit and can ignore the "B" side.
        // This is good because "A"-side line numbers are much easier to correlate with the
        // function locations we have than the "B"-side offsets.
        try {
            final int remBegin = edit.getBeginA();
            final int remEnd = edit.getEndA();

            final boolean logDebug = LOG.isDebugEnabled();

            for (Iterator<Method> fIter = functionsInOldPathByOccurrence.iterator(); fIter.hasNext(); ) {
                Method f = fIter.next();
                if (logDebug) {
                    LOG.debug("Checking " + f);
                }
                if (editOverlaps(f, remBegin, remEnd)) {
                    if (logDebug) {
                        LOG.debug("Edit " + remBegin + ".." + remEnd + " overlaps " + f);
                    }
                    final FunctionChangeHunk.ModificationType modType;
                    if (editDeletes(f, remBegin, remEnd)) {
                        modType = FunctionChangeHunk.ModificationType.DEL;
                    } else {
                        modType = FunctionChangeHunk.ModificationType.MOD;
                    }
                    markFunctionEdit(edit, f, modType);
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
        } finally {
            numHunkInFile++;
        }
    }

    private void markFunctionEdit(Edit edit, Method f, FunctionChangeHunk.ModificationType modType) {
        ChangeHunk hunk = hunkFromEdit(f, edit);
        FunctionChangeHunk fHunk = new FunctionChangeHunk(f, hunk, modType);
        changedFunctionConsumer.accept(fHunk);
        //logEdit(f, edit);
    }

    private ChangeHunk hunkFromEdit(Method f, Edit edit) {
        final int fBegin = f.start1 - 1;
        final int fEnd = f.end1 - 1;
        final int remBegin = edit.getBeginA();
        final int remEnd = edit.getEndA();
        final int linesDeleted;
        final int linesAdded;

        if (remBegin <= fBegin) {
            if (remEnd >= fEnd) {
                // Edit deletes the function
                linesDeleted = f.getGrossLoc();
                linesAdded = Math.min(f.getGrossLoc(), edit.getLengthB());
            } else {
                // Edit starts before the function, and ends within it.
                linesDeleted = remEnd - fBegin;
                // We don't actually know how many lines were added. So we guess.
                linesAdded = Math.min(linesDeleted, edit.getLengthB());
            }
        } else { // remBegin > fBegin // Edit starts within the function
            if (remEnd >= fEnd) {
                // Edit starts within the function, and ends after it.
                linesDeleted = fEnd - remBegin;
                // We don't actually know how many lines were added. So we guess.
                linesAdded = Math.min(linesDeleted, edit.getLengthB());
            } else { // remEnd < fEnd
                // Edit is fully contained within the function.
                linesDeleted = edit.getLengthA();
                linesAdded = edit.getLengthB();
            }
        }
        return new ChangeHunk(commitId, oldPath, this.numHunkInFile, linesDeleted, linesAdded);
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

    private boolean editDeletes(Method func, final int editBegin, final int editEnd) {
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
//            LOG.debug("Delete detected. Edit end: " + editEnd + "; function end: " + fEnd);
//        }
//
//        return result;
    }
}
