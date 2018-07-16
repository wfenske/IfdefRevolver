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

    public final Set<String> additionalGuessedAdds;

    public final Set<String> nonDeletingCommitsToFunctionAndAliases;

    private final CommitsDistanceDb commitsDistanceDb;

    private AgeRequestStats ageRequestStats;

    public FunctionHistory(FunctionId function, Set<FunctionId> olderFunctionIds, Set<String> knownAddsForFunction,
                           Set<String> guessedAddsForFunction, Set<String> additionalGuessedAdds,
                           Set<String> nonDeletingCommitsToFunctionAndAliases,
                           CommitsDistanceDb commitsDistanceDb) {
        this.function = function;
        this.olderFunctionIds = olderFunctionIds;
        this.knownAddsForFunction = knownAddsForFunction;
        this.guessedAddsForFunction = guessedAddsForFunction;
        this.additionalGuessedAdds = additionalGuessedAdds;
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

        final Optional<Integer> ageAmongKnownAdds = computeMaxDistance(currentCommit, knownAddsForFunction);
        if (ageAmongKnownAdds.isPresent()) {
            ageRequestStats.increaseActualAge();
            return ageAmongKnownAdds.get();
        }

        LOG.warn("Forced to assume alternative adding commit. Function: " + this.function +
                " Current commit: " + currentCommit);
//        if (this.guessedAddsForFunction.contains(currentCommit)) {
//            ageRequestStats.increaseGuessed0Age();
//            return 0;
//        }

        final Optional<Integer> guessedAge = computeMaxDistance(currentCommit, guessedAddsForFunction);
        final Optional<Integer> additionalGuessedAge = computeMaxDistance(currentCommit, additionalGuessedAdds);

        if (guessedAge.isPresent()) {
            if (additionalGuessedAge.isPresent()) {
                final int winner = Math.max(guessedAge.get(), additionalGuessedAge.get());
                return useGuessedAge(winner);
            }
            return useGuessedAge(guessedAge.get());
        } else if (additionalGuessedAge.isPresent()) {
            return useGuessedAge(additionalGuessedAge.get());
        }

        LOG.warn("No age at all! Function: " + this.function + " Current commit: " + currentCommit);

        ageRequestStats.increaseNoAgeAtAll();
        return Integer.MAX_VALUE;
    }

    private int useGuessedAge(int age) {
        if (age == 0) {
            ageRequestStats.increaseGuessed0Age();
        } else {
            ageRequestStats.increaseGuessedOtherAge();
        }
        return age;
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

        final Distances nonDelEditDist = computeMinDistances(currentCommit, nonDeletingCommitsToFunctionAndAliases);
        final int nonDelEditWinner = nonDelEditDist.getWinner();

        if (nonDelEditWinner == 0) {
            LOG.warn("Distance to most recent commits is 0, although it is not an adding commit. Function: "
                    + function + " Commit: " + currentCommit);
        }

        return nonDelEditWinner;
    }

    private static class Distances {
        final int dist;
        final int distIncluding0;

        public Distances(int dist, int distIncluding0) {
            this.dist = dist;
            this.distIncluding0 = distIncluding0;
        }

        public int getWinner() {
            return (dist < Integer.MAX_VALUE) ? dist : distIncluding0;
        }
    }

    private Distances computeMinDistances(final String currentCommit, final Collection<String> otherCommits) {
        int minDist = Integer.MAX_VALUE;
        int minDistIncluding0 = Integer.MAX_VALUE;

        for (String otherCommit : otherCommits) {
            if (currentCommit.equals(otherCommit)) {
                minDistIncluding0 = 0;
                continue;
            }
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

        return new Distances(minDist, minDistIncluding0);
    }

    private Optional<Integer> computeMaxDistance(final String currentCommit, final Collection<String> otherCommits) {
        int maxDist = Integer.MIN_VALUE;

        for (String otherCommit : otherCommits) {
            Optional<Integer> currentDist = commitsDistanceDb.minDistance(currentCommit, otherCommit);
            if (!currentDist.isPresent()) continue;
            final int currentDistValue = currentDist.get();
            maxDist = Math.max(maxDist, currentDistValue);
        }

        if (maxDist > Integer.MIN_VALUE) return Optional.of(maxDist);
        else return Optional.empty();
    }
}
