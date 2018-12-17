package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.skunk.util.GroupingHashSetMap;
import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

class FunctionInBranchFactory {
    private static Logger LOG = Logger.getLogger(FunctionInBranch.class);
    private Queue<Integer> unusedUids = new LinkedList<>();
    private int nextUid = 0;
    private GroupingHashSetMap<Integer, FunctionInBranch> functionsByUid = new GroupingHashSetMap<>();

    public synchronized FunctionInBranch create(FunctionId firstId) {
        int uid = getNextUnusedUid();
        FunctionInBranch result = new FunctionInBranch(firstId, uid, this);
        functionsByUid.put(uid, result);
        return result;
    }

    public synchronized void markAsSame(FunctionInBranch a, FunctionInBranch b) {
        final int aUid = a.uid;
        final int bUid = b.uid;
        if (aUid == bUid) return;

        if (!unusedUids.contains(bUid)) {
            unusedUids.offer(bUid);
        }

        final Set<FunctionInBranch> functionsWithSameUidAsB = functionsByUid.remove(bUid);
        for (FunctionInBranch functionLikeB : functionsWithSameUidAsB) {
            functionLikeB.uid = aUid;
            functionsByUid.put(aUid, functionLikeB);
        }
    }

    private int getNextUnusedUid() {
        if (unusedUids.isEmpty()) {
            return nextUid++;
        } else {
            return unusedUids.poll();
        }
    }

    public Collection<? extends Set<FunctionInBranch>> getFunctionsWithSameUid() {
        return functionsByUid.getMap().values();
    }
}
