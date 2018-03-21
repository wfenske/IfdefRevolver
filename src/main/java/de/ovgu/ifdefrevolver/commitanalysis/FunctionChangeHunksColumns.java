package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.skunk.detection.output.CsvColumnValueProvider;
import de.ovgu.skunk.detection.output.CsvRowProvider;

/**
 * <p>Describes the columns of the CSV file that lists all the individual hunks of change to functions defined in the C
 * files within an individual snapshot.</p> <p> Created by wfenske on 28.02.17. </p>
 */
public enum FunctionChangeHunksColumns implements CsvColumnValueProvider<FunctionChangeHunk, Snapshot> {
    /**
     * Function signature (includes return type, name, parameter list)
     */
    FUNCTION_SIGNATURE {
        @Override
        public String csvColumnValue(FunctionChangeHunk changedFunc, Snapshot snapshot) {
            return changedFunc.getFunction().functionSignatureXml;
        }
    },
    /**
     * File containing the definition (usually a <code>.c</code> file)
     */
    FILE {
        @Override
        public String csvColumnValue(FunctionChangeHunk changedFunc, Snapshot snapshot) {
            return changedFunc.getFunction().FilePathForDisplay();
        }
    },
    /**
     * cppstats-normalized LOC of the function
     */
    FUNCTION_GROSS_LOC {
        @Override
        public Integer csvColumnValue(FunctionChangeHunk changedFunc, Snapshot snapshot) {
            return changedFunc.getFunction().getGrossLoc();
        }
    },
    /**
     * Commit that changed the function
     */
    COMMIT_ID {
        @Override
        public String csvColumnValue(FunctionChangeHunk changedFunc, Snapshot snapshot) {
            return changedFunc.getHunk().getCommitId();
        }
    },
    /**
     * Contains <code>1</code> if the commit is a bug-fix commit, else <code>0</code>
     */
    BUGFIX {
        @Override
        public Integer csvColumnValue(FunctionChangeHunk changedFunc, Snapshot snapshot) {
            String commitId = changedFunc.getHunk().getCommitId();
            if (snapshot.isBugfixCommit(commitId)) {
                return 1;
            } else {
                return 0;
            }
        }
    },
    /**
     * Tally of lines deleted and lines added to the function (will be positive if more lines were added than deleted)
     */
    LINE_DELTA {
        @Override
        public Integer csvColumnValue(FunctionChangeHunk changedFunc, Snapshot snapshot) {
            return changedFunc.getHunk().getDelta();
        }
    },
    /**
     * Lines removed by this edit to the function
     */
    LINES_DELETED {
        @Override
        public Integer csvColumnValue(FunctionChangeHunk changedFunc, Snapshot snapshot) {
            return changedFunc.getHunk().getLinesDeleted();
        }
    },
    /**
     * Lines added by this edit to the function
     */
    LINES_ADDED {
        @Override
        public Integer csvColumnValue(FunctionChangeHunk changedFunc, Snapshot snapshot) {
            return changedFunc.getHunk().getLinesAdded();
        }
    },
    /**
     * {@code true} if this function is deleted (or simply moved to a new location) by this change hunk
     */
    FUNCTION_DELETE {
        @Override
        public Boolean csvColumnValue(FunctionChangeHunk changedFunc, Snapshot snapshot) {
            return changedFunc.deletesFunction();
        }
    },
    /**
     * Type of function modification. Can be ADD, DEL, MOD or MOVE.
     */
    MOD_TYPE {
        @Override
        public String csvColumnValue(FunctionChangeHunk changedFunc, Snapshot snapshot) {
            return changedFunc.getModType().name();
        }
    },
    /**
     * New file where the function resides. Will be different from FILE in case the function was moved to another file.
     */
    NEW_FILE {
        @Override
        public String csvColumnValue(FunctionChangeHunk changedFunc, Snapshot snapshot) {
            return changedFunc.getHunk().getNewPath();
        }
    };

    public static CsvRowProvider<FunctionChangeHunk, Snapshot, FunctionChangeHunksColumns>
    newCsvRowProviderForSnapshot(Snapshot snapshot) {
        return new CsvRowProvider<>(FunctionChangeHunksColumns.class, snapshot);
    }

    /**
     * Basename of the CSV file that will hold this information.  It will be located within the results directory, in a
     * project- and snapshot-specific directory, such as <code>results/busybox/2000-04-08</code>
     */
    public static final String FILE_BASENAME = "function_change_hunks.csv";
}
