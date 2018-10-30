package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;

import java.util.Optional;

class AgeAndDistance {
    final Optional<Integer> age;
    final Optional<Integer> distance;
    final String ageString;
    final String distanceString;

    public AgeAndDistance(Optional<Integer> age, Optional<Integer> distance) {
        this.age = age;
        this.ageString = stringFromOptionalInteger(age);
        this.distance = distance;
        this.distanceString = stringFromOptionalInteger(distance);
    }

    private static String stringFromOptionalInteger(Optional<Integer> i) {
        if (i.isPresent()) return Integer.toString(i.get());
        else return "";
    }

    public static AgeAndDistance fromHistoryAndCommit(FunctionHistory history, Commit currentCommit) {
        final Optional<Integer> age = history.getFunctionAgeAtCommit(currentCommit);

        final Optional<Integer> distance = history.getMinDistToPreviousEdit(currentCommit);

        return new AgeAndDistance(age, distance);
    }
}
