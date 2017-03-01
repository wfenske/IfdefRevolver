package de.ovgu.skunk.commitanalysis;

import de.ovgu.skunk.bugs.correlate.data.Snapshot;
import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.detection.output.CsvColumnValueProvider;
import de.ovgu.skunk.detection.output.CsvRowProvider;

import java.text.SimpleDateFormat;
import java.util.Map;

/**
 * <p>Describes the columns of the CSV file listing all the functions defined in the C files within an individual snapshot.</p>
 * <p>
 * Created by wfenske on 28.02.17.
 * </p>
 */
public enum ChangedSnapshotFunctionsColumns implements CsvColumnValueProvider<Map.Entry<Method, Integer>, Snapshot> {

    /**
     * Date of the snapshot in YYYY-MM-DD format
     */
    SNAPSHOT_DATE {
        @Override
        public String csvColumnValue(Map.Entry<Method, Integer> changedFunc, Snapshot snapshot) {
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
        public String csvColumnValue(Map.Entry<Method, Integer> changedFunc, Snapshot snapshot) {
            return changedFunc.getKey().functionSignatureXml;
        }
    },
    /**
     * File containing the definition (usually a <code>.c</code> file)
     */
    FILE {
        @Override
        public String csvColumnValue(Map.Entry<Method, Integer> changedFunc, Snapshot snapshot) {
            return changedFunc.getKey().FilePathForDisplay();
        }

    },
    /**
     * Number of changes within the snapshot
     */
    CHANGES {
        @Override
        public Integer csvColumnValue(Map.Entry<Method, Integer> changedFunc, Snapshot snapshot) {
            return changedFunc.getValue();
        }
    },
    /**
     * cppstats-normalized LOC
     */
    LOC {
        @Override
        public Integer csvColumnValue(Map.Entry<Method, Integer> changedFunc, Snapshot snapshot) {
            return changedFunc.getKey().loc;
        }
    };

    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    public static CsvRowProvider<Map.Entry<Method, Integer>, Snapshot, ChangedSnapshotFunctionsColumns>
    newCsvRowProviderForSnapshot(Snapshot snapshot) {
        return new CsvRowProvider<>(ChangedSnapshotFunctionsColumns.class, snapshot);
    }

    /**
     * Basename of the CSV file that will hold this information.  It will be located within the results directory, in a
     * project- and snapshot-specific directory, such as <code>results/busybox/2000-04-08</code>
     */
    public static final String FILE_BASENAME = "changed_functions.csv";
}
