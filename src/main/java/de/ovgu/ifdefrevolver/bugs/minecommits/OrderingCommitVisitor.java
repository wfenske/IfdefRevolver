package de.ovgu.ifdefrevolver.bugs.minecommits;

import de.ovgu.skunk.detection.output.CsvEnumUtils;
import org.apache.log4j.Logger;
import org.repodriller.domain.Commit;
import org.repodriller.domain.Modification;
import org.repodriller.persistence.PersistenceMechanism;
import org.repodriller.scm.CommitVisitor;
import org.repodriller.scm.SCMRepository;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class OrderingCommitVisitor implements CommitVisitor {
    private static Logger LOG = Logger.getLogger(OrderingCommitVisitor.class);

    private final DateFormat dateFormat = new SimpleDateFormat(OrderedRevisionsColumns.TIMESTAMP_FORMAT);

    public OrderingCommitVisitor() {
    }

    Map<String, OrderedCommit> commitsWithoutParents = new HashMap<>();
    Map<String, OrderedCommit> allCommitsByHash = new HashMap<>();

    int commitsSeen = 0;

    @Override
    public synchronized void process(SCMRepository repo, Commit commit, PersistenceMechanism writer) {
        commitsSeen++;
        LOG.info("Listing commit " + commitsSeen);

        boolean commitModifiesCFile = commitModifiesCFile(commit);

        Calendar cal = commit.getDate();
        String formattedTimeStamp;
        synchronized (dateFormat) {
            formattedTimeStamp = dateFormat.format(cal.getTime());
        }

        final String commitHash = commit.getHash();
        OrderedCommit orderedCommit = new OrderedCommit(commitHash, cal, formattedTimeStamp, commit.getParent(),
                commit.isMerge(), commitModifiesCFile);

        allCommitsByHash.put(commitHash, orderedCommit);
        if (orderedCommit.isRoot()) {
            commitsWithoutParents.put(commitHash, orderedCommit);
        } else {
            String parentHash = orderedCommit.getParentHash().get();
            OrderedCommit parent = allCommitsByHash.get(parentHash);
            if ((parent != null) && !parent.hasChild()) {
                orderedCommit.assignParent(parent);
            } else {
                commitsWithoutParents.put(commitHash, orderedCommit);
            }
        }
    }

    private synchronized void assignMissingParents() {
        final int oldNumParentsWithoutCommits = commitsWithoutParents.size();
        LOG.info("Trying to find parents for " + oldNumParentsWithoutCommits + " commits.");
        int parentsAssigned = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Iterator<OrderedCommit> cIt = commitsWithoutParents.values().iterator(); cIt.hasNext(); ) {
                OrderedCommit c = cIt.next();
                Optional<String> parentHash = c.getParentHash();
                if (!parentHash.isPresent()) continue;
                OrderedCommit parent = allCommitsByHash.get(parentHash.get());
                if ((parent != null) && !parent.hasChild()) {
                    c.assignParent(parent);
                    cIt.remove();
                    changed = true;
                    parentsAssigned++;
                }
            }
        }

        final int newNumParentsWithoutCommits = commitsWithoutParents.size();
        LOG.info("Established " + parentsAssigned + " new parent/child relationships. Commits without parents reduced from " + oldNumParentsWithoutCommits + " to " + newNumParentsWithoutCommits + ".");
    }

    private void tryToBuildLongerBranches() {
        LOG.info("Trying to build longer branches.");
        final int MAGIC_UPPER_BOUNDARY_1 = allCommitsByHash.size() + 1000;
        int numberOfReassignments = 0;
        int iRunCommitsWithTakenParents;
        for (iRunCommitsWithTakenParents = 0; iRunCommitsWithTakenParents < MAGIC_UPPER_BOUNDARY_1; iRunCommitsWithTakenParents++) {
            boolean foundBetterParentsForCommitsWithTakenParents = tryAssignBetterParentsToCommitsWithTakenParents();
            if (foundBetterParentsForCommitsWithTakenParents) {
                numberOfReassignments++;
            } else {
                break;
            }
        }

        LOG.info("Reassigned " + numberOfReassignments + " parent/child relationships in total in " + iRunCommitsWithTakenParents + " iteration(s).");
    }

    private boolean tryAssignBetterParentsToCommitsWithTakenParents() {
        boolean changed = false;
        Set<OrderedCommit> commitsWithTakenParents = findCommitsWithTakenParents();
        LOG.debug("Found " + commitsWithTakenParents.size() + " commits whose parent is already assigned elsewhere.");
        for (OrderedCommit c : commitsWithTakenParents) {
            boolean parentReassigned = tryAssignBetterParent(c);
            if (parentReassigned) {
                changed = true;
                break;
            }
        }
        return changed;
    }

    /**
     * @param c
     * @return <code>true</code> if the parent/child relationship of the commit was changed
     */
    private boolean tryAssignBetterParent(OrderedCommit c) {
        Optional<String> parentHash = c.getParentHash();
        if (!parentHash.isPresent()) {
            return false;
        }
        String parentHashValue = parentHash.get();
        OrderedCommit parent = allCommitsByHash.get(parentHashValue);
        if (parent == null) {
            LOG.warn("Commit " + c.getHash() + " refers to unknown parent: " + parentHashValue);
            return false;
        }
        if (isCommitBetterChildOf(c, parent)) {
            makeCommitChildOf(c, parent);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Turns <code>c</code> into the child of <code>parent</code> and updates the map {@link #commitsWithoutParents}.
     *
     * @param newChild The new child
     * @param parent   The parent
     */
    private void makeCommitChildOf(OrderedCommit newChild, OrderedCommit parent) {
        Optional<OrderedCommit> oldChild = newChild.assignParent(parent);
        if (oldChild.isPresent()) {
            OrderedCommit oldChildValue = oldChild.get();
            commitsWithoutParents.put(oldChildValue.getHash(), oldChildValue);
        }
        commitsWithoutParents.remove(newChild.getHash());
    }

    /**
     * @param potentialNewChild
     * @param parent
     * @return <code>true</code> iff the chain of commits starting with <code>parent</code> becomes longer with
     * <code>potentialNewChild</code> as the child of <code>parent</code>.
     */
    private boolean isCommitBetterChildOf(OrderedCommit potentialNewChild, OrderedCommit parent) {
        Optional<OrderedCommit> currentChild = parent.getChild();
        final int currentNumberOfChildrenOfParent;
        if (currentChild.isPresent()) {
            OrderedCommit currentChildValue = currentChild.get();
            if (currentChildValue == potentialNewChild) {
                return false;
            }
            currentNumberOfChildrenOfParent = currentChildValue.countDescendants();
        } else {
            currentNumberOfChildrenOfParent = 0;
        }
        return (potentialNewChild.countDescendants() > currentNumberOfChildrenOfParent);
    }

    private Set<OrderedCommit> findCommitsWithTakenParents() {
        Set<OrderedCommit> commitsWithTakenParents = new HashSet<>();
        for (Map.Entry<String, OrderedCommit> e : commitsWithoutParents.entrySet()) {
            OrderedCommit c = e.getValue();
            Optional<String> parentHash = c.getParentHash();
            if (parentHash.isPresent()) {
                commitsWithTakenParents.add(c);
            }
        }
        return commitsWithTakenParents;
    }

    private synchronized List<OrderedCommit> getCommitsInOrder() {
        assignMissingParents();
        tryToBuildLongerBranches();
        LOG.info("Ordering commits by branch and timestamp.");
        List<OrderedCommit> roots = new ArrayList<>(commitsWithoutParents.values());
        Collections.sort(roots, OrderedCommit.ORDER_BY_TIMESTAMP);
        List<OrderedCommit> result = new ArrayList<>();
        int branchNumber = 0;
        OrderedCommit lastRoot = null;
        for (OrderedCommit root : roots) {
            if (lastRoot != null) {
                LOG.info("last ts: " + lastRoot.formattedTimestamp + " new ts: " + root.formattedTimestamp + " cmp: " + OrderedCommit.ORDER_BY_TIMESTAMP.compare(lastRoot, root));
            }
            lastRoot = root;
            branchNumber++;
            //printBranch(root, branchNumber);
            appendCommitsInBranch(root, result, branchNumber);
        }
        LOG.info("Found " + result.size() + " commits in " + branchNumber + " branches.");
        return result;
    }

    private void appendCommitsInBranch(OrderedCommit root, List<OrderedCommit> result, int branchNumber) {
        OrderedCommit point = root;
        int branchPosition = 1;
        while (true) {
            point.setBranchNumber(branchNumber);
            boolean includeCommit = isIncludeCommit(point);
            if (includeCommit) {
                point.setBranchPosition(branchPosition);
                branchPosition++;
                result.add(point);
            }
            Optional<OrderedCommit> child = point.getChild();
            if (!child.isPresent()) break;
            point = child.get();
        }
    }

    private boolean isIncludeCommit(OrderedCommit point) {
        boolean includeCommit = true;
        if (point.isMerge()) {
            LOG.info("Ignoring commit " + point.getHash() + ": is a merge.");
            includeCommit = false;
        }
        if (!point.isModifiesCFile()) {
            LOG.info("Ignoring commit " + point.getHash() + ": no .c files are modified.");
            includeCommit = false;
        }
        return includeCommit;
    }

    private boolean commitModifiesCFile(Commit commit) {
        for (Modification m : commit.getModifications()) {
            String fileName = m.getFileName();
            if (fileName.endsWith(".c") || fileName.endsWith(".C")) {
                return true;
            }
        }
        return false;
    }

    private boolean finalized = false;

    @Override
    public synchronized void finalize(SCMRepository repo, PersistenceMechanism writer) {
        if (finalized) return;
        finalized = true;
        List<OrderedCommit> commitsInOrder = this.getCommitsInOrder();
        OrderedRevisionsColumns[] columns = OrderedRevisionsColumns.values();
        for (OrderedCommit c : commitsInOrder) {
            Object line[] = new Object[columns.length];
            for (int iCol = 0; iCol < columns.length; iCol++) {
                OrderedRevisionsColumns column = columns[iCol];
                line[iCol] = column.stringValue(c);
            }
            writer.write(line);
        }
    }

    @Override
    public String name() {
        return "branch ordering commit visitor";
    }

    public String[] getOutputFileHeader() {
        return CsvEnumUtils.headerRowStrings(OrderedRevisionsColumns.class);
    }
}
