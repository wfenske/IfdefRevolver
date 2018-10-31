package de.ovgu.ifdefrevolver.bugs.correlate.output;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.distances.AddChangeDistances;
import de.ovgu.skunk.detection.output.CsvColumnValueProvider;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public enum JointFunctionAbSmellAgeSnapshotColumns implements CsvColumnValueProvider<AddChangeDistances.SnapshotFunctionGenealogy, Void> {
    SNAPSHOT_DATE {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getSnapshot().getFormattedSnapshotDate();
        }
    },
    SNAPSHOT_INDEX {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getSnapshot().getSnapshotIndex();
        }
    },
    SNAPSHOT_BRANCH {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getSnapshot().getBranch();
        }
    },
    START_FUNCTION_SIGNATURE {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getFunctionIdAtStart().signature;
        }
    },
    START_FILE {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getFunctionIdAtStart().file;
        }
    },
    END_FUNCTION_SIGNATURE {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getFunctionIdAtEnd().signature;
        }
    },
    END_FILE {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getFunctionIdAtEnd().file;
        }
    },
    FUNCTION_LOC {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getAnnotationData().getLoc();
        }
    },
    AGE {
        @Override
        public String csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            Optional<Integer> v = in.getCommitsSinceCreation();
            if (v.isPresent()) return Integer.toString(v.get());
            else return "";
        }
    },
    LAST_EDIT {
        @Override
        public String csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            Optional<Integer> v = in.getCommitsSinceLastEdit();
            if (v.isPresent()) return Integer.toString(v.get());
            else return "";
        }
    },

    //HUNKS,
    COMMITS {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            Set<CommitsDistanceDb.Commit> distinctCommits = new HashSet<>();
            for (FunctionChangeRow r : in.getChanges()) {
                distinctCommits.add(r.commit);
            }
            return distinctCommits.size();
        }
    },
    //BUGFIXES,

    LINES_CHANGED {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            int result = 0;
            for (FunctionChangeRow r : in.getChanges()) {
                if (isLineChangeCountable(r)) {
                    result += Math.abs(r.linesAdded);
                    result += Math.abs(r.linesDeleted);
                }
            }
            return result;
        }
    },
    //LINE_DELTA,
    LINES_DELETED {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            int result = 0;
            for (FunctionChangeRow r : in.getChanges()) {
                if (isLineChangeCountable(r)) {
                    result += Math.abs(r.linesDeleted);
                }
            }
            return result;
        }
    },
    LINES_ADDED {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            int result = 0;
            for (FunctionChangeRow r : in.getChanges()) {
                if (isLineChangeCountable(r)) {
                    result += Math.abs(r.linesAdded);
                }
            }
            return result;
        }
    },
    //ABSmell,LocationSmell,ConstantsSmell,NestingSmell,
    LOAC {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getAnnotationData().getLoac();
        }
    },
    LOFC {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getAnnotationData().getLofc();
        }
    },
    FL {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getAnnotationData().getNoFl();
        }
    },
    FC_Dup {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getAnnotationData().getNoFcDup();
        }
    },
    FC_NonDup {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getAnnotationData().getNoFcNonDup();
        }
    },
    ND {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getAnnotationData().getNoNest();
        }
    },
    NEG {
        @Override
        public Object csvColumnValue(AddChangeDistances.SnapshotFunctionGenealogy in, Void ctx) {
            return in.getAnnotationData().getNoNeg();
        }
    };

    /**
     * Basename of the CSV file that will hold this information.  It will be located within the results directory, in a
     * project- and snapshot-specific directory, such as <code>results/busybox/2000-04-08</code>
     */
    public static final String FILE_BASENAME = "joint_function_ab_smell_age_snapshot.csv";

    private static boolean isLineChangeCountable(FunctionChangeRow r) {
        switch (r.modType) {
            case ADD:
            case DEL:
            case MOVE:
                return false;
            //case MOD:
            default:
                return true;
        }
    }
}
