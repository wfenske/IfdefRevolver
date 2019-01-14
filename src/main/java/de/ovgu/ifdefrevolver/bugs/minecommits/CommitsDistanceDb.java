package de.ovgu.ifdefrevolver.bugs.minecommits;

import de.ovgu.ifdefrevolver.util.ProgressMonitor;
import de.ovgu.skunk.util.LinkedGroupingLinkedHashSetMap;
import org.apache.log4j.Logger;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by wfenske on 2018-02-08
 */
public class CommitsDistanceDb {
    private static Logger LOG = Logger.getLogger(CommitsDistanceDb.class);
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE;
    private static final Optional<Integer> DIST_ZERO = Optional.of(0);

    private boolean preprocessed = false;

    public static final class Commit implements Comparable<Commit> {
        private static final Commit[] EMPTY_COMMITS_ARRAY = new Commit[0];

        public static Comparator<Commit> BY_TIMESTAMP_FIRST = new Comparator<Commit>() {
            @Override
            public int compare(Commit a, Commit b) {
                int r;
                r = a.timestamp.compareTo(b.timestamp);
                if (r != 0) return r;
                r = a.compareTo(b);
                return r;
            }
        };

        private final CommitsDistanceDb db;
        public final String commitHash;
        public final int key;
        private String timestamp;
        private Commit[] parents = EMPTY_COMMITS_ARRAY;
        private Commit[] children = EMPTY_COMMITS_ARRAY;

        private Commit(String commitHash, int key, CommitsDistanceDb db) {
            this.db = db;
            this.commitHash = commitHash;
            this.key = key;
        }

        private void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public Date getTimestamp() {
            DateFormat df = new SimpleDateFormat(OrderedRevisionsColumns.TIMESTAMP_FORMAT);
            Date parsedTimestamp;
            try {
                parsedTimestamp = df.parse(timestamp);
            } catch (ParseException e) {
                throw new RuntimeException("Invalid timestamp format: " + timestamp, e);
            }
            return parsedTimestamp;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Commit{");
            sb.append('\'').append(commitHash).append('\'');
            sb.append(", key=").append(key);
            sb.append(", timestamp='").append(timestamp).append('\'');
            sb.append('}');
            return sb.toString();
        }

//        public boolean isRelatedTo(Commit other) {
//            return db.areCommitsRelated(this, other);
//        }

        /**
         * Determine whether the <code>this</code> is a descendant of the given commit, <code>possibleAncestor</code>.
         * Note that every commit is its own descendant.
         *
         * @param possibleAncestor A commit that might be an ancestor of <code>this</code> commit
         * @return <code>true</code> if and only if <code>this</code> is a descendant of (or the same as) the given
         * commit
         */
        public boolean isDescendant(Commit possibleAncestor) {
            return db.isDescendant(this, possibleAncestor);
        }

        /**
         * @param descendant A commit that is supposed to be an descendant of <code>this</code>
         * @return distance from this commit to the descendant commit; or {@link Optional#empty()} if there is no
         * possible path
         */
        public Optional<Integer> minDistance(Commit descendant) {
            return db.minDistance(descendant, this);
        }

        public Commit[] parents() {
            return this.parents;
        }

//        public Set<Commit> siblings() {
//            Set<Commit> siblings = new HashSet<>();
//            for (Commit parent : this.parents) {
//                for (Commit child : parent.children()) {
//                    if (child != this) {
//                        siblings.add(child);
//                    }
//                }
//            }
//            return siblings;
//        }

        public Commit[] children() {
            return this.children;
        }

        public boolean isMerge() {
            return this.parents.length > 1;
        }

        public boolean isBugfix() {
            return false;
        }

        @Override
        public int compareTo(Commit o) {
            return this.key - o.key;
        }
    }

    private static class CacheKey {
        final int child;
        final int ancestor;

        public CacheKey(int child, int ancestor) {
            this.child = child;
            this.ancestor = ancestor;
        }

        @Override
        public int hashCode() {
            return child ^ ancestor;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (!(obj instanceof CacheKey)) return false;
            CacheKey other = (CacheKey) obj;
            return (other.ancestor == this.ancestor) && (other.child == this.child);
        }
    }

    /**
     * Map from commit to the (possibly empty) set of parent commits
     */
    private LinkedGroupingLinkedHashSetMap<Commit, Commit> parents = new LinkedGroupingLinkedHashSetMap<>();

    /**
     * Map from commit to the (possibly empty) set of child commits
     */
    private LinkedGroupingLinkedHashSetMap<Commit, Commit> children = new LinkedGroupingLinkedHashSetMap<>();

    /**
     * Map of the commit objects that have been assigned to each hash.
     */
    private Map<String, Commit> commitsFromHashes = new LinkedHashMap<>();

    /**
     * Same as {@link #parents}, but the hashes have been encoded as integers.
     */
    int[][] intParents;

    /**
     * Map from child commit (first dimension index) to ancestor commits (second dimension index)
     */
    BitSet[] reachables;

    private boolean isReachable(int child, int ancestor) {
        return isReachable(reachables[child], ancestor);
    }

    private static boolean isReachable(BitSet ancestors, int ancestor) {
        return ancestors.get(ancestor);
    }

    private static void setReachable(BitSet ancestors, int ancestor) {
        ancestors.set(ancestor);
    }

    private void setReachables(int child, BitSet ancestors) {
        reachables[child] = ancestors;
    }

    private BitSet getReachables(int child) {
        return reachables[child];
    }

    private BitSet newReachablesColumn() {
        int sz = getNumCommits();
        return new BitSet(sz);
    }

    public Set<Commit> getCommits() {
        return new LinkedHashSet<>(commitsFromHashes.values());
    }

    public int getNumCommits() {
        return commitsFromHashes.size();
    }

    public synchronized Commit findCommitOrDie(String commitHash) {
        Commit result = commitsFromHashes.get(commitHash);
        if (result == null) {
            throw new IllegalArgumentException("Unknown commit: " + commitHash);
        }
        return result;
    }

    private CommitsDistanceDb() {

    }

    public static CommitsDistanceDb fromProtoCommits(Collection<ProtoCommit> protoCommits) {
        List<ProtoCommit> sorted = new ArrayList<>(protoCommits);
        Collections.sort(sorted);
        CommitsDistanceDb db = new CommitsDistanceDb();
        for (ProtoCommit c : sorted) {
            db.put(c);
        }
        return db;
    }

    private synchronized Commit internCommit(String commitHash) {
        //ensurePreprocessed();

        Commit result = commitsFromHashes.get(commitHash);
        if (result != null) return result;

        if (commitHash == null) {
            throw new NullPointerException("Commit hash must not be null");
        }

        int key = commitsFromHashes.size();
        result = new Commit(commitHash, key, this);
        commitsFromHashes.put(commitHash, result);

        return result;
    }

    private void populateIntParents() {
        intParents = new int[parents.getMap().size()][];
        for (Map.Entry<Commit, Set<Commit>> e : parents.getMap().entrySet()) {
            final int childKey = e.getKey().key;
            final Commit[] parentsArray = toSortedCommitArray(e.getValue());
            int[] parentKeys = new int[parentsArray.length];
            int ixInsert = 0;
            for (Commit parent : parentsArray) {
                parentKeys[ixInsert++] = parent.key;
            }
            intParents[childKey] = parentKeys;
        }
//        LOG.warn("Remove the following code!");
//        for (int i = 0; i < intParents.length; i++) {
//            if (intParents[i] == null) {
//                throw new NullPointerException("No parents for commit " + i);
//            }
//        }
    }

    private void populateCommitParents() {
        for (Map.Entry<Commit, Set<Commit>> e : parents.getMap().entrySet()) {
            Commit c = e.getKey();
            c.parents = toSortedCommitArray(e.getValue());
        }
    }

    private void populateCommitChildren() {
        for (Map.Entry<Commit, Set<Commit>> e : children.getMap().entrySet()) {
            Commit c = e.getKey();
            c.children = toSortedCommitArray(e.getValue());
        }
    }

    private static Commit[] toSortedCommitArray(Set<Commit> commits) {
        if (commits == null) commits = Collections.emptySet();
        Commit[] children = new Commit[commits.size()];
        int ixInsert = 0;
        for (Commit c : commits) {
            children[ixInsert++] = c;
        }
        Arrays.sort(children);
        return children;
    }

    private void populateReachables() {
        LOG.debug("Computing reachable commits");
        final int numCommits = getNumCommits();

        ProgressMonitor pm = new ProgressMonitor(numCommits) {
            @Override
            protected void reportIntermediateProgress() {
                LOG.debug("Computed reachable commit " + ticksDone + "/" + ticksTotal + "(" + this.numberOfCurrentReport + "%)");
            }

            @Override
            protected void reportFinished() {
                LOG.debug("Done computing " + ticksTotal + " reachable commits");
            }
        };

        this.reachables = new BitSet[numCommits];
        for (int childCommit = 0; childCommit < numCommits; childCommit++) {
            setReachables(childCommit, computeReachables(childCommit));
            pm.increaseDone();
        }

        maybeLogReachableStats(numCommits);
    }

    private void maybeLogReachableStats(int numCommits) {
        if (LOG.isDebugEnabled()) {
            long setBits = 0;
            for (int childCommit = 0; childCommit < numCommits; childCommit++) {
                BitSet r = getReachables(childCommit);
                setBits += r.cardinality();
            }
            float percentageSet = (100.f * setBits) / numCommits / numCommits;
            LOG.debug(String.format("%.1f%%", percentageSet) + " of all reachables are set.");
        }
    }

    private BitSet computeReachables(int childCommit) {
        if (getReachables(childCommit) != null) {
            return getReachables(childCommit);
        }

        //LOG.debug("Computing reachable commit " + childCommit);

        BitSet reachableFromHere = newReachablesColumn();
        // A commit can always reach itself.
        setReachable(reachableFromHere, childCommit);

        // Common case: Just a single parent
        int[] currentParents = intParents[childCommit];
        while (currentParents.length == 1) {
            int parent = currentParents[0];
            setReachable(reachableFromHere, parent);
            currentParents = intParents[parent];
        }

        // More than one parent case
        for (int parent : currentParents) {
            setReachable(reachableFromHere, parent);

            BitSet parentReachables = getReachables(parent);
            if (parentReachables == null) {
                parentReachables = computeReachables(parent);
            }

            int i = 0;
            while ((i = parentReachables.nextSetBit(i)) >= 0) {
                setReachable(reachableFromHere, i);
                i++;
            }
        }

        setReachables(childCommit, reachableFromHere);
        return reachableFromHere;
    }

    public synchronized void ensurePreprocessed() {
        if (!preprocessed) {
            populateIntParents();
            populateReachables();
            populateCommitParents();
            populateCommitChildren();
            preprocessed = true;
        }
    }

    int requests = 0;
    long firstRequestMillis;

    /**
     * Calculate the length of the shortest path from a child commit to its ancestor
     *
     * @param child    the child commit
     * @param ancestor a preceding commit
     * @return Minimum distance in terms of commits from child to ancestor if there is a path; {@link Optional#empty()}
     * otherwise
     */
    public Optional<Integer> minDistance(String child, String ancestor) {
        ensurePreprocessed();

        if (child == null) {
            throw new NullPointerException("Child commit must not be null");
        }
        if (ancestor == null) {
            throw new NullPointerException("Ancestor commit must not be null");
        }

        final int childKey = findCommitOrDie(child).key;
        final int ancestorKey = findCommitOrDie(ancestor).key;

        return minDistance(childKey, ancestorKey);
    }

    /**
     * Calculate the length of the shortest path from a child commit to its ancestor
     *
     * @param child    the child commit
     * @param ancestor a preceding commit
     * @return Minimum distance in terms of commits from child to ancestor if there is a path; {@link Optional#empty()}
     * otherwise
     */
    public Optional<Integer> minDistance(Commit child, Commit ancestor) {
        ensurePreprocessed();
        validateCommit(child);
        validateCommit(ancestor);
        return minDistance(child.key, ancestor.key);
    }

    private void validateCommit(Commit commit) {
        if (commit.db != this) {
            throw new IllegalArgumentException("Unknown commit: " + commit);
        }
    }

    private Optional<Integer> minDistance(int iChildKey, int iAncestorKey) {
        if (iChildKey == iAncestorKey) {
            return DIST_ZERO;
        }

        if (!isReachable(iChildKey, iAncestorKey)) {
            return Optional.empty();
        }

        CacheKey cacheKey = new CacheKey(iChildKey, iAncestorKey);
        synchronized (knownDistances) {
            Integer cachedDist = knownDistances.get(cacheKey);
            if (cachedDist != null) {
                return optionalizeDistance(cachedDist);
            }
        }

        final int dist;
        if (isReachable(iChildKey, iAncestorKey)) {
            maybeStatPerformance();
            dist = minDistance1(iChildKey, iAncestorKey, cacheKey);
        } else {
            dist = INFINITE_DISTANCE;
        }

        return optionalizeDistance(dist);
    }

    /**
     * Determine whether the first commit is the descendant of the second. Note that every commit is its own
     * descendant.
     *
     * @param descendant the descendant commit
     * @param ancestor   a preceding commit
     * @return <code>true</code> if the descendant commit actually has ancestor among its ancestors, <code>false</code>
     * otherwise.
     */
    public boolean isDescendant(Commit descendant, Commit ancestor) {
        ensurePreprocessed();
        validateCommit(descendant);
        validateCommit(ancestor);
        return isReachable(descendant.key, ancestor.key);
    }

    public int countAncestors(Commit descendant, Commit... ancestors) {
        ensurePreprocessed();
        validateCommit(descendant);
        int numAncestors = 0;

        for (Commit ancestor : ancestors) {
            validateCommit(ancestor);
            if (isReachable(descendant.key, ancestor.key)) {
                numAncestors++;
            }
        }

        return numAncestors;
    }

    private void maybeStatPerformance() {
        if (!LOG.isDebugEnabled()) return;

        if (requests == 0) {
            LOG.debug("First request.");
//                System.out.flush();
//                System.err.flush();
            firstRequestMillis = System.currentTimeMillis();
        } else if ((requests % 40000) == 0) {
            long timeInMillis = System.currentTimeMillis() - firstRequestMillis;
            long timeInSeconds = timeInMillis / 1000;
            LOG.debug("Computation request: " + requests + ". Total time: " + timeInSeconds
                    + " seconds (" + ((timeInMillis * 1000) / requests) + "ns/request). "
                    + "Cache size: " + knownDistances.size() + " entries.");
//                System.out.flush();
//                System.err.flush();
        }
        requests++;
    }

    private static Optional<Integer> optionalizeDistance(int dist) {
        if (dist == INFINITE_DISTANCE) {
            return Optional.empty();
        } else {
            return Optional.of(dist);
        }
    }

    private void put(String commitHash, String... parentHashes) {
        DateFormat format = new SimpleDateFormat(OrderedRevisionsColumns.TIMESTAMP_FORMAT);
        String timestamp = format.format(new Date());
        if (parentHashes.length == 0) {
            put(new ProtoCommit(commitHash, timestamp, Optional.empty()));
        } else {
            for (String parentHash : parentHashes) {
                put(new ProtoCommit(commitHash, timestamp, Optional.of(parentHash)));
            }
        }
    }

    public synchronized void put(ProtoCommit protoCommit) {
        assertNotPreprocessed();
        Commit commit = internCommit(protoCommit.commitHash);
        if (commit.timestamp == null) {
            commit.setTimestamp(protoCommit.timestamp);
        }
        this.parents.ensureMapping(commit);

        if (protoCommit.parentHash.isPresent()) {
            Commit parent = internCommit(protoCommit.parentHash.get());
            this.parents.put(commit, parent);
            this.children.put(parent, commit);
        }
    }

    private synchronized void assertNotPreprocessed() {
        if (preprocessed) {
            throw new IllegalStateException("Cannot modify database after preprocessing.");
        }
    }

    /**
     * Determine all commits that are not a descendant of another commit.
     *
     * @param commits A set of commit hashes
     * @return A subset of the original commits that only holds commits that are not descendants of other commits within
     * the original  set of commits.
     */
    public Set<Commit> filterAncestorCommits(Collection<Commit> commits) {
        ensurePreprocessed();

        for (Commit c : commits) {
            validateCommit(c);
        }

        Set<Commit> commitsWithoutAncestors = new HashSet<>(commits);

        for (Iterator<Commit> it = commitsWithoutAncestors.iterator(); it.hasNext(); ) {
            final Commit descendant = it.next();
            for (Commit ancestor : commits) {
                if (ancestor == descendant) continue;
                if (isReachable(descendant.key, ancestor.key)) {
                    it.remove();
                    break;
                }
            }
        }

        return commitsWithoutAncestors;
    }

    public boolean areCommitsRelated(String c1, String c2) {
        ensurePreprocessed();
        if (c1 == null) {
            throw new NullPointerException("Descendant commit must not be null");
        }
        if (c2 == null) {
            throw new NullPointerException("Ancestor commit must not be null");
        }

        int c1Key = findCommitOrDie(c1).key;
        int c2Key = findCommitOrDie(c2).key;

        int i1 = c1Key;
        int i2 = c2Key;

        return isReachable(i1, i2) || isReachable(i2, i1);
    }

    public boolean areCommitsRelated(Commit c1, Commit c2) {
        ensurePreprocessed();
        validateCommit(c1);
        validateCommit(c2);
        return isReachable(c1.key, c2.key) || isReachable(c2.key, c1.key);
    }

    public Set<Commit> getRoots() {
        ensurePreprocessed();
        Set<Commit> roots = new LinkedHashSet<>();
        for (Commit c : commitsFromHashes.values()) {
            if (c.parents().length == 0) {
                roots.add(c);
            }
        }
        return roots;
    }

    private static void main(String[] args) {
        CommitsDistanceDb db = new CommitsDistanceDb();
//        db.put("D", "C");
//        db.put("C", "B");
//        db.put("B", "A");
//        db.put("A", "(root)");
//        db.put("(root)");
        db.put("(root)");
        db.put("A", "(root)");
        db.put("B", "A");
        db.put("C", "B");
        db.put("D", "C");
        db.put("E", "D", "M");
        db.put("F", "E");
        db.put("G", "I", "F");
        db.put("H", "G");
        db.put("I", "J");
        db.put("J", "K");
        db.put("K", "A");
        db.put("L", "A");
        db.put("M", "L");

        LOG.info("beginning tests");
        test(db, "<unknown-child>", "(root)");
        test(db, "A", "<unknown-ancestor>");
        test(db, "J", "C");
        test(db, "A", "A");
        test(db, "B", "A");
        test(db, "H", "G");
        test(db, "H", "I");
        test(db, "H", "E");
        test(db, "H", "D");
        test(db, "H", "L");
        test(db, "H", "A");
        test(db, "H", "(root)");
        LOG.info("ending tests");
    }

    private static void test(CommitsDistanceDb db, String child, String ancestor) {
        System.out.flush();
        System.err.flush();
        Optional<Integer> dist = db.minDistance(child, ancestor);
        if (dist.isPresent()) {
            System.out.println(child + "->" + ancestor + ": " + dist.get());
        } else {
            System.out.println(child + "->" + ancestor + ": n/a");
        }
        System.out.flush();
        System.err.flush();
    }
}
