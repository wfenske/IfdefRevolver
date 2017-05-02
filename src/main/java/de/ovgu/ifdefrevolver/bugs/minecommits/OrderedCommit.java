package de.ovgu.ifdefrevolver.bugs.minecommits;

import java.util.Comparator;
import java.util.Optional;

/**
 * Created by wfenske on 02.05.17.
 */
public class OrderedCommit {
    final String hash;
    final String formattedTimestamp;
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
            return a.getFormattedTimestamp().compareTo(b.getFormattedTimestamp());
        }
    };

    public OrderedCommit(String hash, String formattedTimestamp, String parentHash, boolean merge, boolean modifiesCFile) {
        this.hash = hash;
        this.formattedTimestamp = formattedTimestamp;
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

    public String getFormattedTimestamp() {
        return formattedTimestamp;
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
        Optional<OrderedCommit> point = Optional.of(this);
        while (point.isPresent()) {
            r++;
            point = point.get().getChild();
        }
        return r;
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
}
