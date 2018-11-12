package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.data.IMinimalSnapshot;
import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.detection.output.CsvColumnValueProvider;
import de.ovgu.skunk.detection.output.CsvRowProvider;

import java.util.Optional;

/**
 * <p>Describes the columns of the CSV file that lists all the individual hunks of change to functions defined in the C
 * files within an individual snapshot.</p> <p> Created by wfenske on 28.02.17. </p>
 */
public enum FunctionChangeHunksColumns implements CsvColumnValueProvider<FunctionChangeHunk, IMinimalSnapshot> {
    /**
     * Function signature (includes return type, name, parameter list)
     */
    FUNCTION_SIGNATURE {
        @Override
        public String csvColumnValue(FunctionChangeHunk changedFunc, IMinimalSnapshot snapshot) {
            return changedFunc.getFunction().uniqueFunctionSignature;
        }
    },
    /**
     * File containing the definition (usually a <code>.c</code> file)
     */
    FILE {
        @Override
        public String csvColumnValue(FunctionChangeHunk changedFunc, IMinimalSnapshot snapshot) {
            return changedFunc.getFunction().FilePathForDisplay();
        }
    },
    /**
     * LOC of the function (incl. whitespace &amp; comments)
     */
    FUNCTION_GROSS_LOC {
        @Override
        public Integer csvColumnValue(FunctionChangeHunk changedFunc, IMinimalSnapshot snapshot) {
            return changedFunc.getFunction().getGrossLoc();
        }
    },
    /**
     * Commit that changed the function
     */
    COMMIT_ID {
        @Override
        public String csvColumnValue(FunctionChangeHunk changedFunc, IMinimalSnapshot snapshot) {
            return changedFunc.getHunk().getCommitId();
        }
    },
    /**
     * Number of the hunk (a.k.a. edit) within the file that this change represents
     */
    HUNK {
        @Override
        public Integer csvColumnValue(FunctionChangeHunk changedFunc, IMinimalSnapshot snapshot) {
            return changedFunc.getHunk().getHunkNo();
        }
    },
    /**
     * Contains <code>1</code> if the commit is a bug-fix commit, else <code>0</code>
     */
    BUGFIX {
        @Override
        public Integer csvColumnValue(FunctionChangeHunk changedFunc, IMinimalSnapshot snapshot) {
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
        public Integer csvColumnValue(FunctionChangeHunk changedFunc, IMinimalSnapshot snapshot) {
            return changedFunc.getHunk().getDelta();
        }
    },
    /**
     * Lines removed by this edit to the function
     */
    LINES_DELETED {
        @Override
        public Integer csvColumnValue(FunctionChangeHunk changedFunc, IMinimalSnapshot snapshot) {
            return changedFunc.getHunk().getLinesDeleted();
        }
    },
    /**
     * Lines added by this edit to the function
     */
    LINES_ADDED {
        @Override
        public Integer csvColumnValue(FunctionChangeHunk changedFunc, IMinimalSnapshot snapshot) {
            return changedFunc.getHunk().getLinesAdded();
        }
    },
    /**
     * Type of function modification. Can be ADD, DEL, MOD or MOVE.
     */
    MOD_TYPE {
        @Override
        public String csvColumnValue(FunctionChangeHunk changedFunc, IMinimalSnapshot snapshot) {
            return changedFunc.getModType().name();
        }
    },
    /**
     * New signature of the function. Will be different from FUNCTION_SIGNATURE if the signature was modified (e.g., new
     * names, changes to parameter list)
     */
    NEW_FUNCTION_SIGNATURE {
        @Override
        public String csvColumnValue(FunctionChangeHunk changedFunc, IMinimalSnapshot snapshot) {
            Optional<Method> newFunction = changedFunc.getNewFunction();
            if (newFunction.isPresent()) {
                return newFunction.get().uniqueFunctionSignature;
            } else return "";
        }
    },
    /**
     * New file where the function resides. Will be different from FILE in case the function was moved to another file.
     */
    NEW_FILE {
        @Override
        public String csvColumnValue(FunctionChangeHunk changedFunc, IMinimalSnapshot snapshot) {
            Optional<Method> newFunction = changedFunc.getNewFunction();
            if (newFunction.isPresent()) {
                return newFunction.get().FilePathForDisplay();
            } else return "";
        }
    };

    public static CsvRowProvider<FunctionChangeHunk, IMinimalSnapshot, FunctionChangeHunksColumns>
    newCsvRowProviderForSnapshot(IMinimalSnapshot snapshot) {
        return new CsvRowProvider<>(FunctionChangeHunksColumns.class, snapshot);
    }

    /**
     * Basename of the CSV file that will hold this information.  It will be located within the results directory, in a
     * project- and snapshot-specific directory, such as <code>results/busybox/2000-04-08</code>
     */
    public static final String FILE_BASENAME = "function_change_hunks.csv";
}
