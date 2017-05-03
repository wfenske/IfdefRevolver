package de.ovgu.ifdefrevolver.bugs.minecommits;

import de.ovgu.ifdefrevolver.util.DateUtils;

import java.util.*;

/**
 * Created by wfenske on 02.05.17.
 */
public class OrderedCommit {
    final String hash;
    final Calendar timestamp;
    final Optional<String> parentHash;
    final boolean merge;
    final boolean modifiesCFile;
    Optional<OrderedCommit> parent = Optional.empty();
    Optional<OrderedCommit> child = Optional.empty();
    /**
     * number assigned to this chain of parent-child related commits
     */
    int branchNumber = -1;
    /**
     * Position of this commit within the branch
     */
    int branchPosition = -1;

    public static final Comparator<OrderedCommit> ORDER_BY_TIMESTAMP = new Comparator<OrderedCommit>() {
        @Override
        public int compare(OrderedCommit a, OrderedCommit b) {
            return a.timestamp.compareTo(b.timestamp);
        }
    };

    public OrderedCommit(String hash, Calendar timestamp, String parentHash, boolean merge, boolean modifiesCFile) {
        this.hash = hash;
        this.timestamp = timestamp;
        if (parentHash == null || parentHash.isEmpty()) {
            this.parentHash = Optional.empty();
        } else {
            this.parentHash = Optional.of(parentHash);
        }
        this.merge = merge;
        this.modifiesCFile = modifiesCFile;
    }

    public String getHash() {
        return hash;
    }

    public Optional<String> getParentHash() {
        return parentHash;
    }

    public boolean isRoot() {
        return !parentHash.isPresent();
    }

    public boolean isMerge() {
        return merge;
    }

    public boolean isModifiesCFile() {
        return modifiesCFile;
    }

    /**
     * Assigns the given to commit to be the parent of <code>this</code>.  If the given commit already had another
     * child, that parent/child link is severed.
     *
     * @param parent The commit that will become the parent of <code>this</code>
     * @return The previous child of <code>parent</code>, if any
     */
    public Optional<OrderedCommit> assignParent(OrderedCommit parent) {
        Optional<OrderedCommit> oldChild = parent.severParentChildLink();
        this.parent = Optional.of(parent);
        parent.setChild(this);
        return oldChild;
    }

    private Optional<OrderedCommit> severParentChildLink() {
        Optional<OrderedCommit> oldChild = this.child;
        if (oldChild.isPresent()) {
            OrderedCommit oldChildValue = oldChild.get();
            oldChildValue.parent = Optional.empty();
            this.child = Optional.empty();
        }
        return oldChild;
    }

    private void setChild(OrderedCommit child) {
        this.child = Optional.of(child);
    }

    public Optional<OrderedCommit> getChild() {
        return this.child;
    }

    public boolean hasChild() {
        return this.child.isPresent();
    }

    /**
     * @return Number of children of this commit, including this commit
     */
    public int countDescendants() {
        int r = 0;
        for (Iterator<OrderedCommit> it = descendantsIterator(); it.hasNext(); it.next()) {
            r++;
        }
        return r;
    }

    private static class DescendentsIterator implements Iterator<OrderedCommit> {
        private Optional<OrderedCommit> point;

        public DescendentsIterator(OrderedCommit root) {
            this.point = Optional.ofNullable(root);
        }

        @Override
        public boolean hasNext() {
            return point.isPresent();
        }

        @Override
        public OrderedCommit next() {
            if (!point.isPresent()) {
                throw new NoSuchElementException();
            }
            OrderedCommit pointValue = point.get();
            point = pointValue.getChild();
            return pointValue;
        }
    }

    public Iterator<OrderedCommit> descendantsIterator() {
        return new DescendentsIterator(this);
    }

    /**
     * Creates a fresh list of this commit and all of its descendants.  Changes to this list will not affect the
     * parent/child relationships in this chain of commits.  Neither will such changes be reflected in this list.
     *
     * @return A fresh list of this commit and all of its descendants.
     */
    public List<OrderedCommit> descendants() {
        List<OrderedCommit> result = new LinkedList<>();
        for (Iterator<OrderedCommit> it = descendantsIterator(); it.hasNext(); ) {
            result.add(it.next());
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderedCommit)) return false;

        OrderedCommit that = (OrderedCommit) o;

        return hash.equals(that.hash);
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    public int getBranchNumber() {
        return branchNumber;
    }

    public void setBranchNumber(int branchNumber) {
        this.branchNumber = branchNumber;
    }

    public int getBranchPosition() {
        return branchPosition;
    }

    public void setBranchPosition(int branchPosition) {
        this.branchPosition = branchPosition;
    }

    public Calendar getTimestamp() {
        return timestamp;
    }

    public void advanceTimestampOneDay() {
        timestamp.add(Calendar.DATE, 1);
    }

    public boolean isAtLeastOneDayBefore(OrderedCommit other) {
        return DateUtils.isAtLeastOneDayBefore(this.timestamp, other.timestamp);
    }
}
