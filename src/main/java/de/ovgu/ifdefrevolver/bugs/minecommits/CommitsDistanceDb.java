package de.ovgu.ifdefrevolver.bugs.minecommits;

import org.apache.log4j.Logger;

import java.util.*;

/**
 * Created by wfenske on 2018-02-08
 */
public class CommitsDistanceDb {
    private static Logger LOG = Logger.getLogger(CommitsDistanceDb.class);
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE;
    private static final Optional<Integer> DIST_ZERO = Optional.of(0);

    private boolean preprocessed = false;

    public static final class Commit {
        private final CommitsDistanceDb db;
        public final String commitHash;
        public final int key;

        private Commit(String commitHash, int key, CommitsDistanceDb db) {
            this.db = db;
            this.commitHash = commitHash;
            this.key = key;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Commit{");
            sb.append("commitHash='").append(commitHash).append('\'');
            sb.append(", key=").append(key);
            sb.append('}');
            return sb.toString();
        }

        public boolean isRelatedTo(Commit other) {
            return db.areCommitsRelated(this, other);
        }
    }

    /**
     * Map from commit hashes to the (possibly empty) set of parent hashes
     */
    Map<String, Set<String>> parents = new HashMap<>();

    /**
     * Simply all commits, in order of appearance
     */
    Set<String> allCommits = new LinkedHashSet<>();

    /**
     * Map of the integers that have been assigned to each hash.
     */
    Map<String, Integer> intsFromHashes = new HashMap<>();

    private Map<String, Commit> commitsFromHashes = new HashMap<>();

    /**
     * Same as {@link #parents}, but the hashes have been encoded as integers.
     */
    int[][] intParents;

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

    Map<CacheKey, Integer> knownDistances = new HashMap<>();
    /**
     * Map from child commit (first dimension index) to ancestor commits (second dimension index)
     */
    //long[][] reachables;
    BitSet[] reachables;

    private boolean isReachable(int child, int ancestor) {
        return isReachable(reachables[child], ancestor);
    }

    private static boolean isReachable(BitSet ancestors, int ancestor) {
        //return ancestors[ancestor] != 0;
        //long mask = 1l << (ancestor & 63);
        //long field = ancestors[ancestor >> 6];
        //return ((field & mask) != 0);
        return ancestors.get(ancestor);
    }

    private static void setReachable(BitSet ancestors, int ancestor) {
//        long mask = 1l << (ancestor & 63);
//        long field = ancestors[ancestor >> 6];
//        ancestors[ancestor >> 6] = (field | mask);
        ancestors.set(ancestor);
    }

    private void setReachables(int child, BitSet ancestors) {
        reachables[child] = ancestors;
    }

    private BitSet getReachables(int child) {
        return reachables[child];
    }

    private BitSet newReachablesColumn() {
        int sz = intsFromHashes.size();
        //int szAdj = sz >> 6;
        //return new long[szAdj + 1];
        return new BitSet(sz);
    }

    /**
     * Calculate the length of the shortest path from a child commit to its ancestor
     *
     * @param child    the child commit
     * @param ancestor a preceding commit
     * @return Minimum distance in terms of commits from child to ancestor; {@link #INFINITE_DISTANCE} if no path can be
     * found
     */
    int minDistance1(int child, int ancestor, CacheKey cacheKey) {
        // Test for end of recursion
        if (child == ancestor) {
            return 0;
        }

        int[] currentParents = intParents[child];

        // Optimization for the common case that a commit has exactly one parent
        int distanceLocallyTraveled = 1;
        while (currentParents.length == 1) {
            int parent = currentParents[0];
            if (parent == ancestor) {
                return distanceLocallyTraveled;
            }
            currentParents = intParents[parent];
            // Update iteration
            distanceLocallyTraveled++;
        }

        // Recurse
        int winningDist = INFINITE_DISTANCE;
        for (int i = 0; i < currentParents.length; i++) {
            int currentParent = currentParents[i];
            if (!isReachable(currentParent, ancestor)) continue;

            CacheKey localCacheKey = new CacheKey(currentParent, ancestor);
            Integer cachedDist;
            synchronized (knownDistances) {
                cachedDist = knownDistances.get(localCacheKey);
            }

            int nextDist;
            if (cachedDist != null) {
                nextDist = cachedDist;
            } else {
                nextDist = minDistance1(currentParent, ancestor, localCacheKey);
            }
            if (nextDist < winningDist) {
                winningDist = nextDist;
            }
        }

        // Return result
        int result;
        if (winningDist < INFINITE_DISTANCE) {
            result = winningDist + distanceLocallyTraveled;
        } else {
            result = INFINITE_DISTANCE;
        }

        if (cacheKey != null) {
            synchronized (knownDistances) {
                knownDistances.put(cacheKey, result);
            }
        }

        return result;
    }

    private void encodeHashesAsInts() {
        int intKey = 0;
        for (String hash : parents.keySet()) {
            intsFromHashes.put(hash, intKey);
            intKey++;
        }
    }

    public Set<String> getCommits() {
        return allCommits;
    }

    public synchronized Commit internCommit(String commitHash) {
        ensurePreprocessed();

        Commit result = commitsFromHashes.get(commitHash);
        if (result == null) {
            if (commitHash == null) {
                throw new NullPointerException("Commit must not be null");
            }

            Integer key = intsFromHashes.get(commitHash);
            if (key == null) {
                throw new IllegalArgumentException("Unknown commit: " + commitHash);
            }
            result = new Commit(commitHash, key, this);
            commitsFromHashes.put(commitHash, result);
        }
        return result;
    }

    private void populateIntParents() {
        intParents = new int[parents.size()][];
        for (Map.Entry<String, Set<String>> e : parents.entrySet()) {
            Integer childKey = intsFromHashes.get(e.getKey());
            if (childKey == null) {
                throw new IllegalStateException("Unknown child commit: `" + e.getKey() + "'");
            }
            Set<Integer> existingParents = new HashSet<>();
            for (String parentHash : e.getValue()) {
                Integer parentKey = intsFromHashes.get(parentHash);
                if (parentKey == null) {
                    LOG.info("Unknown parent commit: `" + parentHash + "'");
                } else {
                    existingParents.add(parentKey);
                }
            }
            int[] existingParentsArray = toIntArray(existingParents);
            intParents[childKey] = existingParentsArray;
        }
//        LOG.warn("Remove the following code!");
//        for (int i = 0; i < intParents.length; i++) {
//            if (intParents[i] == null) {
//                throw new NullPointerException("No parents for commit " + i);
//            }
//        }
    }

    private void populateReachables() {
        LOG.debug("Computing reachable commits");
        final int numCommits = intsFromHashes.size();
        this.reachables = new BitSet[numCommits];
        for (int childCommit = 0; childCommit < numCommits; childCommit++) {
            setReachables(childCommit, computeReachables(childCommit));
        }
        LOG.debug("Done computing reachable commits");
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
            for (int i = 0; i < parentReachables.length(); i++) {
                if (isReachable(parentReachables, i)) {
                    setReachable(reachableFromHere, i);
                }
            }
        }

        setReachables(childCommit, reachableFromHere);
        return reachableFromHere;
    }

    public synchronized void ensurePreprocessed() {
        if (!preprocessed) {
            encodeHashesAsInts();
            populateIntParents();
            populateReachables();
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

        Integer childKey = intsFromHashes.get(child);
        if (childKey == null) {
            LOG.warn("Unknown child commit: `" + child + "'");
            return Optional.empty();
        }
        Integer ancestorKey = intsFromHashes.get(ancestor);
        if (ancestorKey == null) {
            LOG.warn("Unknown ancestor commit: `" + ancestor + "'");
            return Optional.empty();
        }

        final int iChildKey = childKey;
        final int iAncestorKey = ancestorKey;

        return minDistance(iChildKey, iAncestorKey);
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
     * Determine whether the first commit is the descendant of the second. Note that every commit is its own descendant.
     *
     * @param descendant the descendant commit
     * @param ancestor   a preceding commit
     * @return <code>true</code> if the descendant commit actually has ancestor among its ancestors, <code>false</code>
     * otherwise.
     */
    public boolean isDescendant(String descendant, String ancestor) {
        ensurePreprocessed();
        if (descendant == null) {
            throw new NullPointerException("Descendant commit must not be null");
        }
        if (ancestor == null) {
            throw new NullPointerException("Ancestor commit must not be null");
        }

        Integer descendantKey = intsFromHashes.get(descendant);
        if (descendantKey == null) {
            LOG.warn("Unknown descendant commit: `" + descendant + "'");
            return false;
        }
        Integer ancestorKey = intsFromHashes.get(ancestor);
        if (ancestorKey == null) {
            LOG.warn("Unknown ancestor commit: `" + ancestor + "'");
            return false;
        }

        return isReachable(descendantKey, ancestorKey);
    }

    /**
     * Determine whether the first commit is the descendant of the second. Note that every commit is its own descendant.
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

    public int countAncestors(String descendant, String... ancestors) {
        ensurePreprocessed();
        if (descendant == null) {
            throw new NullPointerException("Descendant commit must not be null");
        }
        Integer descendantKey = intsFromHashes.get(descendant);
        if (descendantKey == null) {
            LOG.warn("Unknown descendant commit: `" + descendant + "'");
            return 0;
        }
        int numAncestors = 0;

        for (String ancestor : ancestors) {
            Integer ancestorKey = intsFromHashes.get(ancestor);
            if (ancestorKey == null) {
                LOG.warn("Unknown ancestor commit: `" + ancestor + "'");
            } else {
                if (isReachable(descendantKey, ancestorKey)) {
                    numAncestors++;
                }
            }
        }

        return numAncestors;
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

    private static int[] toIntArray(Collection<Integer> integers) {
        int[] result = new int[integers.size()];
        int insertPos = 0;
        for (Integer v : integers) {
            result[insertPos++] = v;
        }
        return result;
    }

    public synchronized void put(String commit, String... parents) {
        assertNotPreprocessed();
        this.allCommits.add(commit);
        Set<String> existingParents = ensureExistingParentsSet(commit);
        for (String parent : parents) {
            existingParents.add(parent);
        }
    }

    public synchronized void put(String commit, Set<String> parents) {
        assertNotPreprocessed();
        this.allCommits.add(commit);
        Set<String> existingParents = ensureExistingParentsSet(commit);
        existingParents.addAll(parents);
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
     * @return A subset of the original commits that only holds commits that are not descendants of other commits
     * within the original  set of commits.
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

    private Set<String> ensureExistingParentsSet(String commit) {
        Set<String> existingParents = this.parents.get(commit);
        if (existingParents == null) {
            existingParents = new HashSet<>();
            this.parents.put(commit, existingParents);
        }
        return existingParents;
    }

    public boolean areCommitsRelated(String c1, String c2) {
        ensurePreprocessed();
        if (c1 == null) {
            throw new NullPointerException("Descendant commit must not be null");
        }
        if (c2 == null) {
            throw new NullPointerException("Ancestor commit must not be null");
        }

        Integer c1Key = intsFromHashes.get(c1);
        if (c1Key == null) {
            LOG.warn("Unknown commit: `" + c1 + "'");
            return false;
        }
        Integer c2Key = intsFromHashes.get(c2);
        if (c2Key == null) {
            LOG.warn("Unknown commit: `" + c2 + "'");
            return false;
        }

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
