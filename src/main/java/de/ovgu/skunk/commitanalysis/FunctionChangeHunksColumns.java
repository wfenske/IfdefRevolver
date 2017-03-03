package de.ovgu.skunk.commitanalysis;

import de.ovgu.skunk.bugs.correlate.data.Snapshot;
import de.ovgu.skunk.detection.output.CsvColumnValueProvider;
import de.ovgu.skunk.detection.output.CsvRowProvider;

/**
 * <p>Describes the columns of the CSV file that lists all the individual hunks of change to functions defined in the C files within an individual snapshot.</p>
 * <p>
 * Created by wfenske on 28.02.17.
 * </p>
 */
public enum FunctionChangeHunksColumns implements CsvColumnValueProvider<FunctionChangeHunk, Snapshot> {
//
//    /**
//     * Date of the snapshot in YYYY-MM-DD format
//     */
//    SNAPSHOT_DATE {
//        @Override
//        public String csvColumnValue(FunctionChangeHunk changedFunc, Snapshot snapshot) {
//            synchronized (dateFormatter) {
//                return dateFormatter.format(snapshot.getSnapshotDate());
//            }
//        }
//    },
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
    FUNCTION_LOC {
        @Override
        public Integer csvColumnValue(FunctionChangeHunk changedFunc, Snapshot snapshot) {
            return changedFunc.getFunction().loc;
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
    };

//    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

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
