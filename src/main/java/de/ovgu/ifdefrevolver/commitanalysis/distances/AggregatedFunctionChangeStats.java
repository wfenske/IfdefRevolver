package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;

import java.util.HashSet;
import java.util.Set;

class AggregatedFunctionChangeStats {
    final int numCommits;
    final int linesChanged, linesAdded, linesDeleted;

    public AggregatedFunctionChangeStats(int numCommits, int linesAdded, int linesDeleted) {
        this.numCommits = numCommits;
        this.linesAdded = linesAdded;
        this.linesDeleted = linesDeleted;
        this.linesChanged = linesAdded + linesDeleted;
    }

    public static AggregatedFunctionChangeStats fromChanges(Set<FunctionChangeRow> changes) {
        int linesAdded = 0;
        int linesDeleted = 0;
        Set<Commit> commits = new HashSet<>();
        for (FunctionChangeRow change : changes) {
            commits.add(change.commit);
            switch (change.modType) {
                case ADD:
                case DEL:
                    continue;
            }
            linesAdded += change.linesAdded;
            linesDeleted += change.linesDeleted;
        }

        return new AggregatedFunctionChangeStats(commits.size(), linesAdded, linesDeleted);
    }
}
