package de.ovgu.ifdefrevolver.bugs.correlate.output;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.commitanalysis.IAbResRow;
import de.ovgu.ifdefrevolver.commitanalysis.branchtraversal.FunctionGenealogy;
import de.ovgu.skunk.detection.output.CsvColumnValueProvider;

import java.util.List;
import java.util.OptionalInt;

public enum JointFunctionAbSmellAgeSnapshotColumns implements CsvColumnValueProvider<FunctionGenealogy, List<Snapshot>> {
    SNAPSHOT_DATE {
        @Override
        public String csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            return window.get(0).getStartDateString();
        }
    },
    SNAPSHOT_INDEX {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            return window.get(0).getIndex();
        }
    },
    FUNCTION_UID {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> ignored) {
            return in.getUid();
        }
    },
    FUNCTION_SIGNATURE {
        @Override
        public String csvColumnValue(FunctionGenealogy in, List<Snapshot> ignored) {
            return in.getFirstId().signature;
        }
    },
    FILE {
        @Override
        public String csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            return in.getFirstId().file;
        }
    },
    AGE {
        @Override
        public String csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            OptionalInt v = in.age(window);
            if (v.isPresent()) return Integer.toString(v.getAsInt());
            else return "";
        }
    },
    LAST_EDIT {
        @Override
        public String csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            OptionalInt v = in.distanceToMostRecentEdit(window);
            if (v.isPresent()) return Integer.toString(v.getAsInt());
            else return "";
        }
    },
    //HUNKS,
    COMMITS {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            return in.countCommitsInWindow(window);
        }
    },
    LINES_CHANGED {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            return in.countLinesChangedInWindow(window);
        }
    },
    LINES_ADDED {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            return in.countLinesAddedInWindow(window);
        }
    },
    //LINE_DELTA,
    LINES_DELETED {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            return in.countLinesDeletedInWindow(window);
        }
    },
    PREVIOUS_COMMITS {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            return in.countCommitsInPreviousSnapshots(window);
        }
    },
    PREVIOUS_LINES_CHANGED {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            return in.countLinesChangedInPreviousSnapshots(window);
        }
    },
    LOC {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getLoc();
        }
    },
    //ABSmell,LocationSmell,ConstantsSmell,NestingSmell,
    LOAC {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getLoac();
        }
    },
    LOFC {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getLofc();
        }
    },
    FL {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getNoFl();
        }
    },
    FC_Dup {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getNoFcDup();
        }
    },
    FC_NonDup {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getNoFcNonDup();
        }
    },
    CND {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getNoNest();
        }
    },
    NEG {
        @Override
        public Object csvColumnValue(FunctionGenealogy in, List<Snapshot> window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getNoNeg();
        }
    };

    /**
     * Basename of the CSV file that will hold this information.  It will be located within the results directory, in a
     * project- and snapshot-specific directory, such as <code>results/busybox/2000-04-08</code>
     */
    public static final String FILE_BASENAME = "joint_function_ab_smell_age_snapshot.csv";
}
