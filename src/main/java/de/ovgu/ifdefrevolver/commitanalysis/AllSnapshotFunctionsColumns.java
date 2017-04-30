package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.detection.output.CsvColumnValueProvider;
import de.ovgu.skunk.detection.output.CsvRowProvider;

import java.text.SimpleDateFormat;

/**
 * <p>Describes the columns of the CSV file listing all the functions defined in the C files within an individual
 * snapshot.</p> <p> Created by wfenske on 28.02.17. </p>
 */
public enum AllSnapshotFunctionsColumns implements CsvColumnValueProvider<Method, Snapshot> {

    /**
     * Date of the snapshot in YYYY-MM-DD format
     */
    SNAPSHOT_DATE {
        @Override
        public String csvColumnValue(Method func, Snapshot snapshot) {
            synchronized (dateFormatter) {
                return dateFormatter.format(snapshot.getSnapshotDate());
            }
        }
    },
    /**
     * Function signature (includes return type, name, parameter list)
     */
    FUNCTION_SIGNATURE {
        @Override
        public String csvColumnValue(Method func, Snapshot snapshot) {
            return func.functionSignatureXml;
        }
    },
    /**
     * File containing the definition (usually a <code>.c</code> file).  The path is relative to the project's
     * repository root, i.e., the snapshot directory is <em>not</em> part of the returned name.
     */
    FILE {
        @Override
        public String csvColumnValue(Method func, Snapshot snapshot) {
            return func.ProjectRelativeFilePath();
        }

    },
    /**
     * cppstats-normalized LOC
     */
    FUNCTION_LOC {
        @Override
        public Integer csvColumnValue(Method func, Snapshot snapshot) {
            return func.getNetLoc();
        }
    };

    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    public static CsvRowProvider<Method, Snapshot, AllSnapshotFunctionsColumns> newCsvRowProviderForSnapshot
            (Snapshot snapshot) {
        return new CsvRowProvider<>(AllSnapshotFunctionsColumns.class, snapshot);
    }

    /**
     * Basename of the CSV file that will hold this information.  It will be located within the results directory, in a
     * project- and snapshot-specific directory, such as <code>results/busybox/2000-04-08</code>
     */
    public static final String FILE_BASENAME = "all_functions.csv";
}
