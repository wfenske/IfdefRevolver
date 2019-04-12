package de.ovgu.ifdefrevolver.bugs.correlate.output;

import de.ovgu.ifdefrevolver.commitanalysis.IAbResRow;
import de.ovgu.ifdefrevolver.commitanalysis.branchtraversal.FunctionGenealogy;
import de.ovgu.ifdefrevolver.commitanalysis.distances.CommitWindow;
import de.ovgu.skunk.detection.output.CsvColumnValueProvider;

import java.util.OptionalInt;

public enum JointDataColumns implements CsvColumnValueProvider<FunctionGenealogy, CommitWindow> {
    SNAPSHOT_DATE {
        @Override
        public String csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            return window.getWindowStartDateString();
        }
    },
    SNAPSHOT_INDEX {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            return window.getWindowIndex();
        }
    },
    FUNCTION_UID {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow ignored) {
            return in.getUid();
        }
    },
    FUNCTION_SIGNATURE {
        @Override
        public String csvColumnValue(FunctionGenealogy in, CommitWindow ignored) {
            return in.getFirstId().signature;
        }
    },
    FILE {
        @Override
        public String csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            return in.getFirstId().file;
        }
    },
    AGE {
        @Override
        public String csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            OptionalInt v = in.age(window);
            if (v.isPresent()) return Integer.toString(v.getAsInt());
            else return "";
        }
    },
    LAST_EDIT {
        @Override
        public String csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            OptionalInt v = in.distanceToMostRecentEdit(window);
            if (v.isPresent()) return Integer.toString(v.getAsInt());
            else return "";
        }
    },
    //HUNKS,
    COMMITS {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            return in.countCommitsInWindow(window);
        }
    },
    LINES_CHANGED {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            return in.countLinesChangedInWindow(window);
        }
    },
    LINES_ADDED {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            return in.countLinesAddedInWindow(window);
        }
    },
    //LINE_DELTA,
    LINES_DELETED {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            return in.countLinesDeletedInWindow(window);
        }
    },
    PREVIOUS_COMMITS {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            return in.countCommitsInPreviousSnapshots(window);
        }
    },
    PREVIOUS_LINES_CHANGED {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            return in.countLinesChangedInPreviousSnapshots(window);
        }
    },
    LOC {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getLoc();
        }
    },
    //ABSmell,LocationSmell,ConstantsSmell,NestingSmell,
    LOAC {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getLoac();
        }
    },
    LOFC {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getLofc();
        }
    },
    FL {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getNoFl();
        }
    },
    FC_Dup {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getNoFcDup();
        }
    },
    FC_NonDup {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getNoFcNonDup();
        }
    },
    CND {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getNoNest();
        }
    },
    NEG {
        @Override
        public Object csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            IAbResRow staticMetrics = in.getStaticMetrics(window);
            return staticMetrics.getNoNeg();
        }
    },
    /**
     * Number of function present the first snapshot of the window
     */
    SYSTEM_SIZE {
        @Override
        public Object csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            return window.getNumberOfFunctions();
        }
    },
    /**
     * For functions that are present in the first snapshot: 0.
     * For functions that were added before the second snapshot: number of commits between the start of the first
     * snapshot and  the addition of the function.  Value is always negative.  Values close to 0 mean the function was
     * added shortly after the first snapshot was taken.  More negative values mean the function was added later.
     */
    DELAY {
        @Override
        public Integer csvColumnValue(FunctionGenealogy in, CommitWindow window) {
            return in.getDelayOfAppearance(window);
        }
    };

    /**
     * Basename of the CSV file that will hold this information.  It will be located within the results directory, in a
     * project- and snapshot-specific directory, such as <code>results/busybox/2000-04-08</code>
     */
    public static final String FILE_BASENAME = "joint_data.csv";
}
