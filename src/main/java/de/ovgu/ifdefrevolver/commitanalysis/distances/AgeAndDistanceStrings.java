package de.ovgu.ifdefrevolver.commitanalysis.distances;

class AgeAndDistanceStrings {
    final String ageString;
    final String distanceString;

    public AgeAndDistanceStrings(String ageString, String distanceString) {
        this.ageString = ageString;
        this.distanceString = distanceString;
    }

    public static AgeAndDistanceStrings fromHistoryAndCommit(FunctionHistory history, String currentCommit) {
        final int minDist = history.getMinDistToPreviousEdit(currentCommit);
        final String minDistStr = minDist < Integer.MAX_VALUE ? Integer.toString(minDist) : "";

        final int age = history.getFunctionAgeAtCommit(currentCommit);
        final String ageStr = age < Integer.MAX_VALUE ? Integer.toString(age) : "";

        return new AgeAndDistanceStrings(ageStr, minDistStr);
    }
}
