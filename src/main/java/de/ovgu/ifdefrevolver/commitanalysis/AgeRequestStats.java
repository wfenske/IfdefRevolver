package de.ovgu.ifdefrevolver.commitanalysis;

public class AgeRequestStats {
    private int ageRequests = 0, actualAge = 0, guessed0Age = 0, guessedOtherAge = 0, noAgeAtAll = 0;
    private int functionsWithoutAnyKnownAddingCommits = 0;

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

    public synchronized void increaseNoAgeAtAll() {
        this.noAgeAtAll++;
    }

    public synchronized void increaseFunctionsWithoutAnyKnownAddingCommits() {
        this.functionsWithoutAnyKnownAddingCommits++;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AgeRequestStats{");
        sb.append("functionsWithoutAnyKnownAddingCommits=").append(functionsWithoutAnyKnownAddingCommits);
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
