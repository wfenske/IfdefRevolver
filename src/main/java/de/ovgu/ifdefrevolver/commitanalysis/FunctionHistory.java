package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public class FunctionHistory {
    private static final Logger LOG = Logger.getLogger(FunctionHistory.class);

    public final FunctionId function;
    public final Set<FunctionId> olderFunctionIds;
    /**
     * All the commits that have created this function or a previous version of it.
     */
    public final Set<String> knownAddsForFunction;

    public final Set<String> guessedAddsForFunction;

    public final Set<String> nonDeletingCommitsToFunctionAndAliases;

    private final CommitsDistanceDb commitsDistanceDb;

    private AgeRequestStats ageRequestStats;

    public FunctionHistory(FunctionId function, Set<FunctionId> olderFunctionIds, Set<String> knownAddsForFunction, Set<String> guessedAddsForFunction, Set<String> nonDeletingCommitsToFunctionAndAliases,
                           CommitsDistanceDb commitsDistanceDb) {
        this.function = function;
        this.olderFunctionIds = olderFunctionIds;
        this.knownAddsForFunction = knownAddsForFunction;
        this.guessedAddsForFunction = guessedAddsForFunction;
        this.nonDeletingCommitsToFunctionAndAliases = nonDeletingCommitsToFunctionAndAliases;
        this.commitsDistanceDb = commitsDistanceDb;
        this.ageRequestStats = new AgeRequestStats();
    }

    public void setAgeRequestStats(AgeRequestStats ageRequestStats) {
        this.ageRequestStats = ageRequestStats;
    }

    /**
     * The age of the function at the current commit, measured in number of commits.
     *
     * @param currentCommit
     * @return The function's age or {@link Integer#MAX_VALUE} if the age cannot be determined or guessed.
     */
    public int getFunctionAgeAtCommit(final String currentCommit) {
        ageRequestStats.increaseAgeRequests();

        if (this.knownAddsForFunction.contains(currentCommit)) {
            ageRequestStats.increaseActualAge();
            return 0;
        }

        int currentMinAgeAmongKnownAdds = Integer.MAX_VALUE;
        for (String addingCommit : this.knownAddsForFunction) {
            Optional<Integer> age = commitsDistanceDb.minDistance(currentCommit, addingCommit);
            if (age.isPresent()) {
                currentMinAgeAmongKnownAdds = Math.min(age.get(), currentMinAgeAmongKnownAdds);
            }
        }

        if (currentMinAgeAmongKnownAdds < Integer.MAX_VALUE) {
            ageRequestStats.increaseActualAge();
            return currentMinAgeAmongKnownAdds;
        }

        LOG.warn("Forced to assume alternative adding commit. Function: " + this.function + " Current commit: " + currentCommit);
        if (this.guessedAddsForFunction.contains(currentCommit)) {
            ageRequestStats.increaseGuessed0Age();
            return 0;
        }

        final MinDistances d = computeMinDistances(currentCommit, guessedAddsForFunction);

        if (d.minDist < Integer.MAX_VALUE) {
            ageRequestStats.increaseGuessedOtherAge();
            return d.minDist;
        }

        if (d.minDistIncluding0 < Integer.MAX_VALUE) {
            ageRequestStats.increaseGuessed0Age();
            return d.minDistIncluding0;
        }

        ageRequestStats.increaseNoAgeAtAll();
        return Integer.MAX_VALUE;
    }

    /**
     * The time since the function was last edited before the current commit, measured in number of commits.
     *
     * @param currentCommit
     * @return The age of the function's last edit or {@link Integer#MAX_VALUE} if the age cannot be determined or guessed.
     */
    public int getMinDistToPreviousEdit(String currentCommit) {
        if (knownAddsForFunction.contains(currentCommit)) {
            return 0;
        }

        if (guessedAddsForFunction.contains(currentCommit)) {
            LOG.warn("Forced to assume 0 edit distance because the commit has no known ancestors among the"
                    + " function's edits. Function: " + function + " Current commit: " + currentCommit);
            return 0;
        }

        final MinDistances d = computeMinDistances(currentCommit, nonDeletingCommitsToFunctionAndAliases);
        final int result = (d.minDist < Integer.MAX_VALUE) ? d.minDist : d.minDistIncluding0;

        if (result == 0) {
            LOG.warn("Distance to most recent commits is 0, although it is not an adding commit. Function: "
                    + function + " Commit: " + currentCommit);
        }

        return result;
    }

    private static class MinDistances {
        final int minDist;
        final int minDistIncluding0;

        public MinDistances(int minDist, int minDistIncluding0) {
            this.minDist = minDist;
            this.minDistIncluding0 = minDistIncluding0;
        }
    }

    private MinDistances computeMinDistances(final String currentCommit, final Collection<String> otherCommits) {
        int minDist = Integer.MAX_VALUE;
        int minDistIncluding0 = Integer.MAX_VALUE;

        for (String otherCommit : otherCommits) {
            if (currentCommit.equals(otherCommit)) continue;
            Optional<Integer> currentDist = commitsDistanceDb.minDistance(currentCommit, otherCommit);
            if (!currentDist.isPresent()) continue;
            final int currentDistValue = currentDist.get();
            if (currentDistValue == 0) {
                LOG.warn("Distance between commits is 0? " + currentCommit + " .. " + otherCommit);
                minDistIncluding0 = Math.min(minDistIncluding0, currentDistValue);
            } else {
                minDist = Math.min(minDist, currentDistValue);
            }
        }

        return new MinDistances(minDist, minDistIncluding0);
    }
}
