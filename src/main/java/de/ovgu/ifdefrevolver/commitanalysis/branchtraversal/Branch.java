package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.AbResRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import org.apache.log4j.Logger;

import java.util.*;

class Branch {
    private static Logger LOG = Logger.getLogger(Branch.class);
    private static final Branch[] EMPTY_PARENT_BRANCHES = new Branch[0];

    protected final Commit firstCommit;
    protected final MoveConflictStats moveConflictStats;
    protected final FunctionInBranchFactory functionFactory;
    
    protected Branch[] parentBranches;
    protected Commit mostRecentCommit;
    protected FunctionsInBranch functions;
    protected List<Commit> directCommits = new ArrayList<>();
    private Map<Commit, PreMergeBranch> preMergeBranches = null;

    protected Branch(Branch[] parentBranches, Commit firstCommit, MoveConflictStats moveConflictStats, FunctionsInBranch functionsInBranch, FunctionInBranchFactory functionFactory) {
        this.parentBranches = parentBranches;
        this.firstCommit = firstCommit;
        this.mostRecentCommit = firstCommit;
        this.moveConflictStats = moveConflictStats;
        this.functions = functionsInBranch;
        this.functions.setBranch(this);
        this.functionFactory = functionFactory;
        this.directCommits.add(firstCommit);
    }

    protected Branch(Branch[] parentBranches, Commit firstCommit, MoveConflictStats moveConflictStats, FunctionInBranchFactory functionFactory) {
        this(parentBranches, firstCommit, moveConflictStats, new FunctionsInBranch(moveConflictStats, functionFactory), functionFactory);
    }

    public void close() {
        this.parentBranches = EMPTY_PARENT_BRANCHES;
        this.functions.close();
        this.directCommits = Collections.emptyList();
        this.preMergeBranches = null;
    }

    public static Branch createRootBranch(Commit commit, MoveConflictStats moveConflictStats, FunctionInBranchFactory functionFactory) {
        return new Branch(new Branch[0], commit, moveConflictStats, functionFactory);
    }

    public static Branch createMergeBranch(Commit mergeCommit, Branch[] parentBranches, List<FunctionChangeRow> mergeChanges) {
        final int numParents = parentBranches.length;
        Branch[] preMergeBranches = new PreMergeBranch[numParents];

        for (int i = 0; i < numParents; i++) {
            Branch parentOfMerge = parentBranches[i];
            PreMergeBranch preMergeBranch = parentOfMerge.getPreMergeBranchOrDie(mergeCommit);
            preMergeBranches[i] = preMergeBranch;
        }

        final Branch firstParent = parentBranches[0];

        Branch result = new Branch(preMergeBranches, mergeCommit, firstParent.moveConflictStats, firstParent.functionFactory);
        result.merge(mergeChanges);

        return result;
    }

    public List<Branch> parentsInLevelOrder() {
        Set<Branch> seen = new LinkedHashSet<>();
        Queue<Branch> parentBranches = new LinkedList<>();
        for (Branch parent : this.parentBranches) {
            parentBranches.offer(parent);
        }

        Branch b;
        while ((b = parentBranches.poll()) != null) {
            seen.add(b);
            for (Branch parent : b.parentBranches) {
                if (!seen.contains(parent)) {
                    parentBranches.offer(parent);
                }
            }
        }

        return new ArrayList<>(seen);
    }

    @Override
    public String toString() {
        return "Branch{" +
                "firstCommit='" + firstCommit.commitHash +
                "', parentBranches=" + parentBranchFirstCommitHashes() +
                '}';
    }

    private String parentBranchFirstCommitHashes() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < parentBranches.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append('\'');
            sb.append(parentBranches[i].firstCommit.commitHash);
            sb.append('\'');
        }
        sb.append(']');
        return sb.toString();
    }

    public Commit getFirstCommit() {
        return this.firstCommit;
    }

    public Branch[] getParentBranches() {
        return parentBranches;
    }

    protected void merge(List<FunctionChangeRow> mergeChanges) {
        validateMerge();

        final int numParents = parentBranches.length;
        PreMergeBranch[] preMergeBranches = new PreMergeBranch[numParents];
        for (int i = 0; i < numParents; i++) {
            preMergeBranches[i] = (PreMergeBranch) parentBranches[i];
        }

        this.functions.merge(preMergeBranches, mergeChanges);
    }

    private void validateMerge() {
        final Commit mergeCommit = this.firstCommit;

        Set<Commit> expectedParents = new HashSet<>();
        for (Commit parent : mergeCommit.parents()) {
            expectedParents.add(parent);
        }

        Set<Commit> actualParents = new HashSet<>();
        for (Branch parent : parentBranches) {
            PreMergeBranch preMergeBranch = (PreMergeBranch) parent;
            if (preMergeBranch.getMergeCommit() != mergeCommit) {
                throw new IllegalArgumentException("Merge commit does not match. Expected=" + mergeCommit.commitHash + " got=" + preMergeBranch.getMergeCommit().commitHash);
            }
            actualParents.add(preMergeBranch.getLastCommitBeforeMerge());
        }

        if (!actualParents.equals(expectedParents)) {
            throw new IllegalArgumentException("Actual and expected parents don't match: expected=" + expectedParents + " got=" + actualParents);
        }
    }

    protected void split() {
        if (parentBranches.length != 1) {
            throw new IllegalArgumentException("Not a branch split: Branch has multiple parents: " + parentBranches);
        }
        this.functions.inherit(parentBranches[0].functions);
    }

    public void logOccurrenceOfFunction(FunctionId id) {
        if (this.functions != null) {
            this.functions.logOccurrenceOfFunction(id);
        }
    }


    public void logOccurrenceOfFunctionInSelfAndParentBranches(FunctionId id) {
        logOccurrenceOfFunction(id);
        for (Branch b : this.parentsInLevelOrder()) {
            b.logOccurrenceOfFunction(id);
        }
    }

    public void addDirectCommit(Commit currentCommit) {
        this.directCommits.add(currentCommit);
        this.mostRecentCommit = currentCommit;
    }

    public Commit getMostRecentCommit() {
        return this.mostRecentCommit;
    }

    public boolean directlyContains(Commit commit) {
        return this.directCommits.contains(commit);
    }

    public boolean isMergeBranch() {
        return this.parentBranches.length > 1;
    }

    public static Branch[] toBranchArray(Branch b) {
        Branch[] result = new Branch[1];
        result[0] = b;
        return result;
    }

    public Branch createSplitBranch(final Commit commit) {
        Branch[] parentBranches = Branch.toBranchArray(this);
        Branch branch = new Branch(parentBranches, commit, moveConflictStats, functionFactory);
        branch.split();
        return branch;
    }

    public PreMergeBranch createPreMergeBranch(final Commit mergeCommit) {
        PreMergeBranch preMergeBranch = getPreMergeBranchOrNull(mergeCommit);
        if (preMergeBranch == null) {
            final Commit lastCommitBeforeMerge = this.getMostRecentCommit();
            preMergeBranch = new PreMergeBranch(this,
                    lastCommitBeforeMerge, mergeCommit, moveConflictStats);
            this.preMergeBranches.put(mergeCommit, preMergeBranch);
        } else {
            throw new IllegalArgumentException("Pre-merge branch for merge " +
                    this.getMostRecentCommit().commitHash + " -> " + mergeCommit.commitHash + " has already been created.");
        }

        return preMergeBranch;
    }

    public PreMergeBranch getPreMergeBranchOrDie(final Commit mergeCommit) {
        PreMergeBranch preMergeBranch = getPreMergeBranchOrNull(mergeCommit);
        if (preMergeBranch != null) {
            return preMergeBranch;
        }
        throw new NullPointerException("Pre-merge branch for merge -> " + mergeCommit.commitHash + " does not exist.");
    }

    private PreMergeBranch getPreMergeBranchOrNull(Commit mergeCommit) {
        if (this.preMergeBranches == null) this.preMergeBranches = new HashMap<>();
        return this.preMergeBranches.get(mergeCommit);
    }

    public Set<FunctionId> getCurrentlyActiveFunctionIds() {
        if (this.functions == null) {
            LOG.warn("getCurrentlyActiveFunctionIds called for obsolete branch");
            return Collections.emptySet();
        }
        return this.functions.getCurrentlyActiveFunctionIds();
    }

    public TrackingErrorStats assignJointFunctionAbSmellRows(Commit commit, List<AbResRow> jointFunctionAbSmellRows) {
        return this.functions.assignJointFunctionAbSmellRows(commit, jointFunctionAbSmellRows);
    }
}
