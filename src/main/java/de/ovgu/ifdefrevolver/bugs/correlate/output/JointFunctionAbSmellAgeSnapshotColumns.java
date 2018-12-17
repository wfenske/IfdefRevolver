package de.ovgu.ifdefrevolver.bugs.correlate.output;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.commitanalysis.branchtraversal.FunctionGenealogy;
import de.ovgu.ifdefrevolver.commitanalysis.branchtraversal.JointFunctionAbSmellRow;
import de.ovgu.skunk.detection.output.CsvColumnValueProvider;

import java.util.OptionalInt;

public enum JointFunctionAbSmellAgeSnapshotColumns implements CsvColumnValueProvider<FunctionGenealogy, Snapshot> {
    SNAPSHOT_DATE {
        @Override
        public String csvColumnValue(FunctionGenealogy in, Snapshot s) {
            return s.getStartDateString();
        }
    },
    SNAPSHOT_INDEX {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, Snapshot s) {
            return s.getIndex();
        }
    },
    FUNCTION_UID {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, Snapshot ignored) {
            return in.getUid();
        }
    },
    FUNCTION_SIGNATURE {
        @Override
        public String csvColumnValue(FunctionGenealogy in, Snapshot ignored) {
            return in.getFirstId().signature;
        }
    },
    FILE {
        @Override
        public String csvColumnValue(FunctionGenealogy in, Snapshot s) {
            return in.getFirstId().file;
        }
    },
    AGE {
        @Override
        public String csvColumnValue(FunctionGenealogy in, Snapshot s) {
            OptionalInt v = in.age(s);
            if (v.isPresent()) return Integer.toString(v.getAsInt());
            else return "";
        }
    },
    LAST_EDIT {
        @Override
        public String csvColumnValue(FunctionGenealogy in, Snapshot s) {
            OptionalInt v = in.distanceToMostRecentEdit(s);
            if (v.isPresent()) return Integer.toString(v.getAsInt());
            else return "";
        }
    },
    //HUNKS,
    COMMITS {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, Snapshot s) {
            return in.countCommitsInSnapshot(s);
        }
    },
    LINES_CHANGED {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, Snapshot s) {
            return in.countLinesChangedInSnapshot(s);
        }
    },
    LINES_ADDED {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, Snapshot s) {
            return in.countLinesAddedInSnapshot(s);
        }
    },
    //LINE_DELTA,
    LINES_DELETED {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, Snapshot s) {
            return in.countLinesDeletedInSnapshot(s);
        }
    },
    LOC {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, Snapshot s) {
            JointFunctionAbSmellRow staticMetrics = in.getStaticMetrics(s);
            return staticMetrics.abResRow.getLoc();
        }
    },
    //ABSmell,LocationSmell,ConstantsSmell,NestingSmell,
    LOAC {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, Snapshot s) {
            JointFunctionAbSmellRow staticMetrics = in.getStaticMetrics(s);
            return staticMetrics.abResRow.getLoac();
        }
    },
    LOFC {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, Snapshot s) {
            JointFunctionAbSmellRow staticMetrics = in.getStaticMetrics(s);
            return staticMetrics.abResRow.getLofc();
        }
    },
    FL {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, Snapshot s) {
            JointFunctionAbSmellRow staticMetrics = in.getStaticMetrics(s);
            return staticMetrics.abResRow.getNoFl();
        }
    },
    FC_Dup {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, Snapshot s) {
            JointFunctionAbSmellRow staticMetrics = in.getStaticMetrics(s);
            return staticMetrics.abResRow.getNoFcDup();
        }
    },
    FC_NonDup {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, Snapshot s) {
            JointFunctionAbSmellRow staticMetrics = in.getStaticMetrics(s);
            return staticMetrics.abResRow.getNoFcNonDup();
        }
    },
    CND {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, Snapshot s) {
            JointFunctionAbSmellRow staticMetrics = in.getStaticMetrics(s);
            return staticMetrics.abResRow.getNoNest();
        }
    },
    NEG {
        @Override
        public Object csvColumnValue(FunctionGenealogy in, Snapshot s) {
            JointFunctionAbSmellRow staticMetrics = in.getStaticMetrics(s);
            return staticMetrics.abResRow.getNoNeg();
        }
    };

    /**
     * Basename of the CSV file that will hold this information.  It will be located within the results directory, in a
     * project- and snapshot-specific directory, such as <code>results/busybox/2000-04-08</code>
     */
    public static final String FILE_BASENAME = "joint_function_ab_smell_age_snapshot.csv";
}
