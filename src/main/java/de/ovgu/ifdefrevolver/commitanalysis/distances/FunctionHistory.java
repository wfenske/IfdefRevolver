package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.AgeRequestStats;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public class FunctionHistory {
    private static final Logger LOG = Logger.getLogger(FunctionHistory.class);

    public final FunctionId function;
    public final Set<FunctionIdWithCommit> olderFunctionIds;
    /**
     * All the commits that have created this function or a previous version of it.
     */
    public final Set<Commit> knownAddsForFunction;

    public final Set<Commit> guessedAddsForFunction;

    public final Set<Commit> additionalGuessedAdds;

    public final Set<Commit> nonDeletingCommitsToFunctionAndAliases;

    private final CommitsDistanceDb commitsDistanceDb;

    private AgeRequestStats ageRequestStats;

    private static final Optional<Integer> ZERO_AGE = Optional.of(0);

    public FunctionHistory(FunctionId function, Set<FunctionIdWithCommit> olderFunctionIds, Set<Commit> knownAddsForFunction,
                           Set<Commit> guessedAddsForFunction, Set<Commit> additionalGuessedAdds,
                           Set<Commit> nonDeletingCommitsToFunctionAndAliases,
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
    public Optional<Integer> getFunctionAgeAtCommit(final Commit currentCommit) {
        ageRequestStats.increaseAgeRequests();

        if (this.knownAddsForFunction.contains(currentCommit)) {
            ageRequestStats.increaseActualAge();
            return ZERO_AGE;
        }

        final Optional<Integer> ageAmongKnownAdds = computeMaxDistance(currentCommit, knownAddsForFunction);
        if (ageAmongKnownAdds.isPresent()) {
            ageRequestStats.increaseActualAge();
            return ageAmongKnownAdds;
        }

//        LOG.warn("Forced to assume alternative adding commit. Function: " + this.function +
//                " Current commit: " + currentCommit);
//        if (this.guessedAddsForFunction.contains(currentCommit)) {
//            ageRequestStats.increaseGuessed0Age();
//            return 0;
//        }

        final Optional<Integer> guessedAge = computeMaxDistance(currentCommit, guessedAddsForFunction);
        final Optional<Integer> additionalGuessedAge = computeMaxDistance(currentCommit, additionalGuessedAdds);

        if (guessedAge.isPresent()) {
            if (additionalGuessedAge.isPresent()) {
                final int winner = Math.max(guessedAge.get(), additionalGuessedAge.get());
                useGuessedAge(winner);
                return Optional.of(winner);
            } else {
                useGuessedAge(guessedAge.get());
                return guessedAge;
            }
        } else if (additionalGuessedAge.isPresent()) {
            useGuessedAge(additionalGuessedAge.get());
            return additionalGuessedAge;
        }

        ageRequestStats.increaseNoAgeAtAll(this.function, currentCommit);
        return Optional.empty();
    }

    private void useGuessedAge(int age) {
        if (age == 0) {
            ageRequestStats.increaseGuessed0Age();
        } else {
            ageRequestStats.increaseGuessedOtherAge();
        }
    }

    /**
     * The time since the function was last edited before the current commit, measured in number of commits.
     *
     * @param currentCommit
     * @return The age of the function's last edit or {@link Integer#MAX_VALUE} if the age cannot be determined or
     * guessed.
     */
    public Optional<Integer> getMinDistToPreviousEdit(Commit currentCommit) {
        if (knownAddsForFunction.contains(currentCommit)) {
            return ZERO_AGE;
        }

        if (guessedAddsForFunction.contains(currentCommit)) {
            LOG.warn("Forced to assume 0 edit distance because the commit has no known ancestors among the"
                    + " function's edits. Function: " + function + " Current commit: " + currentCommit);
            return ZERO_AGE;
        }

        final Distances nonDelEditDist = computeMinDistances(currentCommit, nonDeletingCommitsToFunctionAndAliases);
        final Optional<Integer> nonDelEditWinner = nonDelEditDist.getWinner();

        if (nonDelEditWinner.isPresent() && (nonDelEditWinner.get() == 0)) {
            LOG.warn("Distance to most recent commits is 0, although it is not an adding commit. Function: "
                    + function + " Commit: " + currentCommit);
        }

        return nonDelEditWinner;
    }

    private static class Distances {
        final Optional<Integer> dist;
        final Optional<Integer> distIncluding0;

        public Distances(int dist, int distIncluding0) {
            this.dist = optionalizeInt(dist);
            this.distIncluding0 = optionalizeInt(distIncluding0);
        }

        private static Optional<Integer> optionalizeInt(int i) {
            if (i < Integer.MAX_VALUE) return Optional.of(i);
            else return Optional.empty();
        }

        public Optional<Integer> getWinner() {
            if (dist.isPresent()) {
                return dist;
            } else if (distIncluding0.isPresent()) {
                return distIncluding0;
            } else {
                return Optional.empty();
            }
        }
    }

    private Distances computeMinDistances(final Commit currentCommit, final Collection<Commit> otherCommits) {
        int minDist = Integer.MAX_VALUE;
        int minDistIncluding0 = Integer.MAX_VALUE;

        for (Commit otherCommit : otherCommits) {
            if (currentCommit == otherCommit) {
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

    private Optional<Integer> computeMaxDistance(final Commit currentCommit, final Collection<Commit> otherCommits) {
        int maxDist = Integer.MIN_VALUE;

        for (Commit otherCommit : otherCommits) {
            Optional<Integer> currentDist = commitsDistanceDb.minDistance(currentCommit, otherCommit);
            if (!currentDist.isPresent()) continue;
            final int currentDistValue = currentDist.get();
            maxDist = Math.max(maxDist, currentDistValue);
        }

        if (maxDist > Integer.MIN_VALUE) return Optional.of(maxDist);
        else return Optional.empty();
    }
}
