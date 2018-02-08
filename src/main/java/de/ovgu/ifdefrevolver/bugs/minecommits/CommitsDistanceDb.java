package de.ovgu.ifdefrevolver.bugs.minecommits;

import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by wfenske on 2018-02-08
 */
public class CommitsDistanceDb {
    private static Logger LOG = Logger.getLogger(CommitsDistanceDb.class);
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE;

    /**
     * Map from commit hashes to the (possibly empty) set of parent hashes
     */
    Map<String, String[]> parents = new HashMap<>();

    /**
     * Map of the integers that have been assigned to each hash.
     */
    Map<String, Integer> intsFromHashes = new HashMap<>();

    /**
     * Same as {@link #parents}, but the hashes have been encoded as integers.
     */
    Map<Integer, int[]> intParents = new HashMap<>();

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

        int[] currentParents = intParents.get(child);

        // TODO: Eliminate this case!
        if (currentParents == null) {
            LOG.warn("Commit " + child + " is not known (parents==null). This should not have happened!");
            return INFINITE_DISTANCE;
        }

        // Optimization for the common case that a commit has exactly one parent
        int distanceLocallyTraveled = 1;
        while (currentParents.length == 1) {
            int parent = currentParents[0];
            if (parent == ancestor) {
                return distanceLocallyTraveled;
            }
            currentParents = intParents.get(parent);
            if (currentParents == null) {
                LOG.warn("Commit " + parent + " is not known (parents==null). This should not have happened!");
                return INFINITE_DISTANCE;
            }
            // Update iteration
            distanceLocallyTraveled++;
        }

        // Recurse
        int winningDist = INFINITE_DISTANCE;
        for (int i = 0; i < currentParents.length; i++) {
            int nextDist = minDistance1(currentParents[i], ancestor);
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
        int intKey = 0
        for (String hash : parents.keySet()) {
            intsFromHashes.put(hash, intKey);
            intKey++;
        }
    }

    private void populateIntParents() {
        // TODO
    }

    public static void main(String[] args) {
        CommitsDistanceDb db = new CommitsDistanceDb();
        db.intParents.put(3, new int[]{2});
        db.intParents.put(2, new int[]{1});
        db.intParents.put(1, new int[]{0});
        db.intParents.put(0, new int[0]);
        System.out.println(db.minDistance1(3, 0));
        System.out.println(db.minDistance1(2, 0));
        System.out.println(db.minDistance1(1, 0));
        System.out.println(db.minDistance1(0, 0));
        System.out.println(db.minDistance1(0, 3));
        System.out.println(db.minDistance1(42, 3));
    }
}
