package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class DeletionRecord {
    private static Logger LOG = Logger.getLogger(DeletionRecord.class);

    public final FunctionInBranch function;
    public final Commit deletingCommit;
    public final Branch branch;
    private boolean active = true;

    public static List<DeletionRecord> excludeSuperseded(List<DeletionRecord> records) {
        final boolean logDebug = LOG.isDebugEnabled();

        List<DeletionRecord> result = new ArrayList<>();
        Set<Commit> allCommits = records.stream().map(r -> r.deletingCommit).collect(Collectors.toSet());

        for (DeletionRecord r : records) {
            final Commit deletingCommit = r.deletingCommit;
            if (allCommits.stream().noneMatch(otherCommit -> (otherCommit != deletingCommit) && otherCommit.isDescendantOf(deletingCommit))) {
                result.add(r);
            } else {
                if (logDebug) {
                    Set<String> supersedingCommits = allCommits.stream().filter(otherCommit -> (otherCommit != deletingCommit) && otherCommit.isDescendantOf(deletingCommit)).map(c -> c.commitHash).collect(Collectors.toSet());
                    LOG.debug("Deletion record " + r + " is superseded by at least one other deletion record in " + records);
                    LOG.debug("Current deleting commit: " + deletingCommit.commitHash + " superseding commits=" + supersedingCommits);
                }
            }
        }

        return result;
    }

    public static class DeletionRecordSummary {
        public final int numActiveDeletes;
        public final int numInactiveDeletes;

        private DeletionRecordSummary(int numActiveDeletes, int numInactiveDeletes) {
            this.numActiveDeletes = numActiveDeletes;
            this.numInactiveDeletes = numInactiveDeletes;
        }

        public boolean isAlwaysDeleted() {
            return numInactiveDeletes == 0;
        }

        public boolean isNeverDeleted() {
            return numActiveDeletes == 0;
        }

        public boolean isAmbiguous() {
            return (numActiveDeletes != 0) && (numInactiveDeletes != 0);
        }
    }

    public static DeletionRecordSummary summarizeRecords(Collection<DeletionRecord> records) {
        int numActive = 0;
        int numInactive = 0;

        for (DeletionRecord r : records) {
            if (r.isActive()) numActive++;
            else numInactive++;
        }

        return new DeletionRecordSummary(numActive, numInactive);
    }

    DeletionRecord(FunctionInBranch function, Commit deletingCommit, Branch branch) {
        this.function = function;
        this.deletingCommit = deletingCommit;
        this.branch = branch;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }

    @Override
    public String toString() {
        return "DeletionRecord{" +
                "function=" + function +
                ", deletingCommit=" + deletingCommit +
                ", active=" + active +
                ", branch=" + branch +
                '}';
    }
}
