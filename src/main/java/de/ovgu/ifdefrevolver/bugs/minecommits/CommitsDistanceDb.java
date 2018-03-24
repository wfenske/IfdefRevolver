package de.ovgu.ifdefrevolver.bugs.minecommits;

import org.apache.log4j.Logger;

import java.util.*;

/**
 * Created by wfenske on 2018-02-08
 */
public class CommitsDistanceDb {
    private static Logger LOG = Logger.getLogger(CommitsDistanceDb.class);
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE;

    private boolean preprocessed = false;

    /**
     * Map from commit hashes to the (possibly empty) set of parent hashes
     */
    Map<String, Set<String>> parents = new HashMap<>();

    /**
     * Map of the integers that have been assigned to each hash.
     */
    Map<String, Integer> intsFromHashes = new HashMap<>();

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

    Map<CacheKey, Optional<Integer>> knownDistances = new HashMap<>();
    boolean[][] reachables;

    /**
     * Calculate the length of the shortest path from a child commit to its ancestor
     *
     * @param child    the child commit
     * @param ancestor a preceding commit
     * @return Minimum distance in terms of commits from child to ancestor; {@link #INFINITE_DISTANCE} if no path can be
     * found
     */
    int minDistance1(int child, int ancestor) {
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
            int nextDist = minDistance1(currentParent, ancestor);
            if (nextDist < winningDist) {
                winningDist = nextDist;
            }
        }

        // Return result
        if (winningDist < INFINITE_DISTANCE) {
            return winningDist + distanceLocallyTraveled;
        } else {
            return INFINITE_DISTANCE;
        }
    }

    private void encodeHashesAsInts() {
        int intKey = 0;
        for (String hash : parents.keySet()) {
            intsFromHashes.put(hash, intKey);
            intKey++;
        }
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
        this.reachables = new boolean[numCommits][];
        for (int childCommit = 0; childCommit < numCommits; childCommit++) {
            reachables[childCommit] = computeReachables(childCommit);
        }
        LOG.debug("Done computing reachable commits");
    }

    private boolean[] computeReachables(int childCommit) {
        if (reachables[childCommit] != null) {
            return reachables[childCommit];
        }

        //LOG.debug("Computing reachable commit " + childCommit);

        //Set<Integer> reachableFromHere = new HashSet<>();
        boolean[] reachableFromHere = new boolean[intsFromHashes.size()];
        int[] currentParents = intParents[childCommit];
        // Common case
        while (currentParents.length == 1) {
            int parent = currentParents[0];
            //reachableFromHere.add(parent);
            reachableFromHere[parent] = true;
            currentParents = intParents[parent];
        }
        // More than one parent case
        for (int parent : currentParents) {
            reachableFromHere[parent] = true;
            boolean[] parentReachables = reachables[parent];
            if (parentReachables == null) {
                parentReachables = computeReachables(parent);
            }
            for (int i = 0; i < parentReachables.length; i++) {
                //reachableFromHere.addAll(parentReachables);
                if (parentReachables[i]) {
                    reachableFromHere[i] = true;
                }
            }
        }

        //int[] result = new int[reachableFromHere.size()];
        //int ix = 0;
        //for (Integer c : reachableFromHere) {
        //    result[ix++] = c;
        //}

        //reachables[childCommit] = result;
        reachables[childCommit] = reachableFromHere;
        //return result;
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

        CacheKey cacheKey = new CacheKey(childKey, ancestorKey);
        synchronized (knownDistances) {
            Optional<Integer> cachedDist = knownDistances.get(cacheKey);
            if (cachedDist != null) {
                return cachedDist;
            }
        }

        final int dist;

        if (isReachable(childKey, ancestorKey)) {
            dist = minDistance1(childKey, ancestorKey);
        } else {
            dist = INFINITE_DISTANCE;
        }

        Optional result;
        if (dist == INFINITE_DISTANCE) {
            result = Optional.empty();
        } else {
            result = Optional.of(dist);
        }

        synchronized (knownDistances) {
            knownDistances.put(cacheKey, result);
        }

        return result;
    }

    private boolean isReachable(Integer childKey, int ancestorKey) {
//        int[] allAncestors = reachables[childKey];
//        for (int a : allAncestors) {
//            if (a == ancestorKey) {
//                return true;
//            }
//        }
//        return false;
        return reachables[childKey][ancestorKey];
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
        Set<String> existingParents = ensureExistingParentsSet(commit);
        for (String parent : parents) {
            existingParents.add(parent);
        }
    }

    public synchronized void put(String commit, Set<String> parents) {
        assertNotPreprocessed();
        Set<String> existingParents = ensureExistingParentsSet(commit);
        existingParents.addAll(parents);
    }

    private void assertNotPreprocessed() {
        if (preprocessed) {
            throw new IllegalStateException("Cannot modify database after preprocessing.");
        }
    }

    private Set<String> ensureExistingParentsSet(String commit) {
        Set<String> existingParents = this.parents.get(commit);
        if (existingParents == null) {
            existingParents = new HashSet<>();
            this.parents.put(commit, existingParents);
        }
        return existingParents;
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
