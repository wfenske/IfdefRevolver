package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.commitanalysis.AllFunctionsRow;

import java.util.List;

public class AllFunctionsRowInWindow extends AllFunctionsRow {
    private String firstSnapshotCommit;
    //private Set<String> commitsBefore;
    //private Set<String> commitsIncludingAndAfter;

    public AllFunctionsRowInWindow(AllFunctionsRow function,
                                   List<Snapshot> snapshotsBefore,
                                   List<Snapshot> snapshotsIncludingAndAfter) {
        this.functionId = function.functionId;
        this.loc = function.loc;
        this.firstSnapshotCommit = snapshotsIncludingAndAfter.get(0).getStartHash();

//        this.commitsBefore = new HashSet<>();
//        for (Snapshot s : snapshotsBefore) {
//            commitsBefore.addAll(s.getCommitHashes());
//        }
//
//        this.commitsIncludingAndAfter = new HashSet<>();
//        for (Snapshot s : snapshotsIncludingAndAfter) {
//            commitsIncludingAndAfter.addAll(s.getCommitHashes());
//        }
    }

    public String getFirstSnapshotCommit() {
        return firstSnapshotCommit;
    }
}
