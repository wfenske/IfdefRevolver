package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

public class AgeRequestStats {
    private static final Logger LOG = Logger.getLogger(AgeRequestStats.class);

    private int ageRequests = 0, actualAge = 0, guessed0Age = 0, guessedOtherAge = 0, noAgeAtAll = 0;
    private int functionsWithoutAnyKnownAddingCommits = 0;
    private int uniqueFunctionsWithoutAnyKnownAddingCommits = 0;
    private Set<FunctionId> functionsWithoutAnyKnownAddingCommitsNames = new HashSet<>();
    private Set<FunctionId> functionsWithoutAnyAgeAtAll = new HashSet<>();


    public synchronized void increaseAgeRequests() {
        this.ageRequests++;
    }

    public synchronized void increaseActualAge() {
        this.actualAge++;
    }

    public synchronized void increaseGuessed0Age() {
        this.guessed0Age++;
    }

    public synchronized void increaseGuessedOtherAge() {
        this.guessedOtherAge++;
    }

    public synchronized void increaseNoAgeAtAll(FunctionId function, CommitsDistanceDb.Commit currentCommit) {
        this.noAgeAtAll++;

        boolean weKnowThisAlready = functionsWithoutAnyAgeAtAll.contains(function);

        if (weKnowThisAlready) {
            LOG.info("No age at all! Function: " + function + " (repeated entry for this function).  Current commit: " + currentCommit);
        } else {
            functionsWithoutAnyAgeAtAll.add(function);
            LOG.warn("No age at all! Function: " + function + " Current commit: " + currentCommit);
        }
    }

    public synchronized void increaseFunctionsWithoutAnyKnownAddingCommits(FunctionId function) {
        this.functionsWithoutAnyKnownAddingCommits++;

        boolean weKnowThisAlready = functionsWithoutAnyKnownAddingCommitsNames.contains(function);

        if (weKnowThisAlready) {
            LOG.info("No known creating commits for function " + function + " (repeated entry for this function).");
        } else {
            functionsWithoutAnyKnownAddingCommitsNames.add(function);
            LOG.warn("No known creating commits for function " + function + " (first entry for this function).");
            this.uniqueFunctionsWithoutAnyKnownAddingCommits++;
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AgeRequestStats{");
        sb.append("functionsWithoutAnyKnownAddingCommits=").append(functionsWithoutAnyKnownAddingCommits);
        sb.append(", uniqueFunctionsWithoutAnyKnownAddingCommits=").append(uniqueFunctionsWithoutAnyKnownAddingCommits);
        sb.append(", ageRequests=").append(ageRequests);
        sb.append(", actualAge=").append(actualAge);

        final int totalErrors = guessed0Age + guessedOtherAge + noAgeAtAll;

        sb.append(", errors=").append(totalErrors);
        sb.append(" (").append(percentage(totalErrors, ageRequests)).append("%)");

        sb.append(" {guessed0Age=").append(guessed0Age);
        sb.append(" (").append(percentage(guessed0Age, ageRequests)).append("%)");
        sb.append(", guessedOtherAge=").append(guessedOtherAge);
        sb.append(" (").append(percentage(guessedOtherAge, ageRequests)).append("%)");
        sb.append(", noAgeAtAll=").append(noAgeAtAll);
        sb.append(" (").append(percentage(noAgeAtAll, ageRequests)).append("%)");
        sb.append("}}");
        return sb.toString();
    }

    private int percentage(int fraction, int total) {
        return Math.round(fraction * 100.0f / (total * 1.0f));
    }
}
