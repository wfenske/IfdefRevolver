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

    private final DateFormat dateFormat = new SimpleDateFormat(RevisionsFullColumns.TIMESTAMP_FORMAT);

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
        OrderedCommit orderedCommit = new OrderedCommit(commitHash, formattedTimeStamp, commit.getParent(),
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
        final int MAGIC_UPPER_BOUNDARY = 1000;
        boolean changed = true;
        int numberOfReassignments = 0;
        for (int i = 0; changed && (i < MAGIC_UPPER_BOUNDARY); i++) {
            changed = false;
            Set<OrderedCommit> commitsWithTakenParents = findCommitsWithTakenParents();
            LOG.debug("Found " + commitsWithTakenParents.size() + " commits whose parent is already assigned elsewhere.");
            for (OrderedCommit c : commitsWithTakenParents) {
                OrderedCommit parent = allCommitsByHash.get(c.getParentHash().get());
                // NOTE, 2017-05-02, wf: Parent must be non-null.  After all, this is the list of commits whose
                // parents are already assigned elsewhere.
                Optional<OrderedCommit> currentChild = parent.getChild();
                final int currentNumberOfChildren;
                if (currentChild.isPresent()) {
                    currentNumberOfChildren = currentChild.get().countDescendants();
                } else {
                    currentNumberOfChildren = 0;
                }
                if (c.countDescendants() > currentNumberOfChildren) {
                    Optional<OrderedCommit> oldChild = c.assignParent(parent);
                    if (oldChild.isPresent()) {
                        OrderedCommit oldChildValue = oldChild.get();
                        commitsWithoutParents.put(oldChildValue.getHash(), oldChildValue);
                    }
                    commitsWithoutParents.remove(c.getHash());
                    numberOfReassignments++;
                    changed = true;
                    break;
                }
            }
        }
        LOG.info("Reassigned " + numberOfReassignments + " parent/child relationships.");
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
        List<OrderedCommit> commits = new ArrayList<>(commitsWithoutParents.values());
        List<OrderedCommit> result = new ArrayList<>();
        Collections.sort(commits, OrderedCommit.ORDER_BY_TIMESTAMP);
        int branchNumber = 0;
        for (OrderedCommit root : commits) {
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
            if (fileName.endsWith(".c") || !fileName.endsWith(".C")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void finalize(SCMRepository repo, PersistenceMechanism writer) {
        List<OrderedCommit> commitsInOrder = this.getCommitsInOrder();
        OrderedRevisionsColumns[] columns = OrderedRevisionsColumns.values();
        Object line[] = new Object[columns.length];
        for (OrderedCommit c : commitsInOrder) {
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
