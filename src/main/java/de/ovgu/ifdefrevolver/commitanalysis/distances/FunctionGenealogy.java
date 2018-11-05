package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.ifdefrevolver.util.GroupingHashSetMap;
import org.apache.log4j.Logger;

import java.util.*;

public class FunctionGenealogy {
    private static final Logger LOG = Logger.getLogger(FunctionGenealogy.class);

    public final FunctionIdWithCommit firstId;
    private final Set<FunctionIdWithCommit> functionIdsWithCommits;
    private final List<FunctionIdWithCommit> functionIdsWithCommitsList;
    private final GroupingHashSetMap<FunctionId, Commit> commitsByFunctionId;

    public FunctionGenealogy(Set<FunctionIdWithCommit> functionIdsWithCommits) {
        this.functionIdsWithCommits = functionIdsWithCommits;
        this.functionIdsWithCommitsList = new ArrayList<>(functionIdsWithCommits);
        this.firstId = this.functionIdsWithCommitsList.get(0);
        this.commitsByFunctionId = new GroupingHashSetMap<>();

        for (FunctionIdWithCommit fIdWithCommit : functionIdsWithCommits) {
            commitsByFunctionId.put(fIdWithCommit.functionId, fIdWithCommit.commit);
        }
    }

    public static FunctionGenealogy merge(FunctionGenealogy earlier, FunctionGenealogy later) {
        Set<FunctionIdWithCommit> mergedIdsWithCommits = merge(earlier.functionIdsWithCommitsList, later.functionIdsWithCommitsList);
        if (!earlier.firstId.equals(mergedIdsWithCommits.iterator().next())) {
            throw new RuntimeException("Bad merge!");
        }
        return new FunctionGenealogy(mergedIdsWithCommits);
    }

    private static Set<FunctionIdWithCommit> merge(List<FunctionIdWithCommit> l1, List<FunctionIdWithCommit> l2) {
        Set<FunctionIdWithCommit> result = new LinkedHashSet<>();
        int i1 = 0, i2 = 0;
        final int len1 = l1.size();
        final int len2 = l2.size();

        while ((i1 < len1) && (i2 < len2)) {
            FunctionIdWithCommit c1 = l1.get(i1);
            FunctionIdWithCommit c2 = l2.get(i2);
            if (c1.commit.isDescendant(c2.commit)) { // c1 comes after c2 --> take c2
                result.add(c2);
                i2++;
            } else {
                result.add(c1);
                i1++;
            }
        }

        while (i1 < len1) {
            FunctionIdWithCommit c = l1.get(i1++);
            result.add(c);
        }

        while (i2 < len2) {
            FunctionIdWithCommit c = l2.get(i2++);
            result.add(c);
        }

        return result;
    }

    public int isSuccessor(FunctionGenealogy earlierGenealogy) {
        if (this.firstId.commit == earlierGenealogy.firstId.commit) return -1;
        if (!this.firstId.commit.isDescendant(earlierGenealogy.firstId.commit)) return -1;
//        if (!earlierGenealogy.commitsByFunctionId.containsKey(firstId.functionId)) return -1;
//        if (functionsIdsWithCommitsIntersect(earlierGenealogy)) return 0;
        return earlierGenealogy.mergePosition(this.firstId);
    }

    private int mergePosition(final FunctionIdWithCommit id) {
        final int len = functionIdsWithCommitsList.size();

        for (int i = 0; i < len; i++) {
            FunctionIdWithCommit myId = functionIdsWithCommitsList.get(i);
            if (!myId.functionId.equals(id.functionId)) continue;
            if (!id.commit.isDescendant(myId.commit)) continue;

            final int nextI = i + 1;
            if (nextI >= len) {
                Optional<Integer> dist = myId.commit.minDistance(id.commit);
                if (dist.isPresent()) return dist.get();
                else return -1;
            }

            Commit nextCommit = functionIdsWithCommitsList.get(nextI).commit;
            if (nextCommit.isDescendant(id.commit)) {
                return 0;
            }
        }

        return -1;
    }

    private boolean functionsIdsWithCommitsIntersect(FunctionGenealogy earlierGenealogy) {
        return !Collections.disjoint(this.functionIdsWithCommits, earlierGenealogy.functionIdsWithCommits);
    }

//    public boolean isRelatedTo(FunctionIdWithCommit id) {
//        Set<Commit> otherCommits = commitsByFunctionId.get(id.functionId);
//        if (otherCommits == null) return false;
//
//        final Commit currentCommit = id.commit;
//        for (Commit otherCommit : otherCommits) {
//            if (currentCommit.isRelatedTo(otherCommit)) {
//                return true;
//            }
//        }
//
//        //        for (Commit otherCommit : otherCommits) {
////            if (currentCommit.isRelatedTo(otherCommit)) {
////                return true;
////            } else {
////                //if (currentFunctionId.signature.equals("static int isvalidgroupname(struct berval * name)")) {
////                //LOG.info("Genealogies don't match. FunctionId=" + currentFunctionId + " but commits are unrelated: " + currentCommit + " vs. " + other.commit);
////                //AddChangeDistances.reportFunctionGenealogy(0, possiblyMatchingGenealogy);
////                //}
////            }
////        }
//
////        for (FunctionIdWithCommit otherFunctionIdWithCommit : functionIdsWithCommits) {
////            if (otherFunctionIdWithCommit.commit.isRelatedTo(currentCommit)) {
////                return true;
////            }
////        }
//
//        return false;
//    }

    public <T extends Collection<FunctionIdWithCommit>> T mergeInto(T target) {
        target.addAll(functionIdsWithCommitsList);
        return target;
    }

    public Set<FunctionId> getUniqueFunctionIds() {
        return commitsByFunctionId.getMap().keySet();
    }
}
