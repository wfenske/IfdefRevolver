package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.ifdefrevolver.util.GroupingHashSetMap;
import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.Set;

public class FunctionGenealogy {
    private static final Logger LOG = Logger.getLogger(FunctionGenealogy.class);

    private final Set<FunctionIdWithCommit> functionIdsWithCommits;
    private final GroupingHashSetMap<FunctionId, Commit> commitsByFunctionId;

    public FunctionGenealogy(Set<FunctionIdWithCommit> functionIdsWithCommits) {
        this.functionIdsWithCommits = functionIdsWithCommits;
        this.commitsByFunctionId = new GroupingHashSetMap<>();

        for (FunctionIdWithCommit fIdWithCommit : functionIdsWithCommits) {
            commitsByFunctionId.put(fIdWithCommit.functionId, fIdWithCommit.commit);
        }
    }

    public boolean isRelatedTo(FunctionIdWithCommit id) {
        FunctionId currentFunctionId = id.functionId;
        Commit currentCommit = id.commit;

        Set<Commit> otherCommits = commitsByFunctionId.get(currentFunctionId);
        if (otherCommits == null) return false;

//        for (Commit otherCommit : otherCommits) {
//            if (currentCommit.isRelatedTo(otherCommit)) {
//                return true;
//            } else {
//                //if (currentFunctionId.signature.equals("static int isvalidgroupname(struct berval * name)")) {
//                //LOG.info("Genealogies don't match. FunctionId=" + currentFunctionId + " but commits are unrelated: " + currentCommit + " vs. " + other.commit);
//                //AddChangeDistances.reportFunctionGenealogy(0, possiblyMatchingGenealogy);
//                //}
//            }
//        }
        for (FunctionIdWithCommit otherFunctionIdWithCommit : functionIdsWithCommits) {
            if (otherFunctionIdWithCommit.commit.isRelatedTo(currentCommit)) {
                return true;
            }
        }

        return false;
    }

    public <T extends Collection<FunctionIdWithCommit>> T mergeInto(T target) {
        target.addAll(functionIdsWithCommits);
        return target;
    }

    public Set<FunctionId> getUniqueFunctionIds() {
        return commitsByFunctionId.getMap().keySet();
    }
}
