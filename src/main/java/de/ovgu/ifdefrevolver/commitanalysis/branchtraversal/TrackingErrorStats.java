package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class TrackingErrorStats {
    private int numActualFunctions;
    private int numMatchingFunctions;
    private int numMissingFunctions;
    private int numSuperfluousFunctions;

    public TrackingErrorStats(Set<FunctionId> actualFunctions, Set<FunctionId> computedFunctions) {
        numActualFunctions = actualFunctions.size();

        Set<FunctionId> matchingFunctions = new HashSet<>(actualFunctions);
        matchingFunctions.retainAll(computedFunctions);
        numMatchingFunctions = matchingFunctions.size();

        Set<FunctionId> missingFunctions = new HashSet<>(actualFunctions);
        missingFunctions.removeAll(computedFunctions);
        numMissingFunctions = missingFunctions.size();

        Set<FunctionId> superfluousFunctions = new HashSet<>(computedFunctions);
        superfluousFunctions.removeAll(actualFunctions);
        numSuperfluousFunctions = superfluousFunctions.size();
    }

    public TrackingErrorStats(Collection<TrackingErrorStats> stats) {
        this.numActualFunctions = stats.stream().mapToInt(s -> s.numActualFunctions).sum();
        this.numMatchingFunctions = stats.stream().mapToInt(s -> s.numMatchingFunctions).sum();
        this.numMissingFunctions = stats.stream().mapToInt(s -> s.numMissingFunctions).sum();
        this.numSuperfluousFunctions = stats.stream().mapToInt(s -> s.numSuperfluousFunctions).sum();
    }

    public int getNumActualFunctions() {
        return numActualFunctions;
    }

    public int getNumMissingFunctions() {
        return numMissingFunctions;
    }

    public int getNumSuperfluousFunctions() {
        return numSuperfluousFunctions;
    }

    public int getNumMatchingFunctions() {
        return numMatchingFunctions;
    }

    public int getNumErroneousFunctions() {
        return getNumMissingFunctions() + getNumSuperfluousFunctions();
    }

    public float percentSuperfluousFunctions() {
        return (100.0f * getNumSuperfluousFunctions()) / getNumActualFunctions();
    }

    public float percentMissingFunctions() {
        return (100.0f * getNumMissingFunctions()) / getNumActualFunctions();
    }

    public float percentMatchingFunctions() {
        return (100.0f * getNumMatchingFunctions()) / getNumActualFunctions();
    }

    public float percentErroneousFunctions() {
        return (100.0f * getNumErroneousFunctions()) / getNumActualFunctions();
    }

    private static String formatPercent(float v) {
        return String.format("%.1f%%", v);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TrackingErrorStats{");
        sb.append("#actualFunctions=").append(getNumActualFunctions());

        sb.append(", #matchingFunctions=").append(getNumMatchingFunctions());
        sb.append(" (").append(formatPercent(percentMatchingFunctions())).append(')');

        sb.append(", #erroneousFunctions=").append(getNumErroneousFunctions());
        sb.append(" (").append(formatPercent(percentErroneousFunctions())).append(')');

        sb.append(", #missingFunctions=").append(getNumMissingFunctions());
        sb.append(" (").append(formatPercent(percentMissingFunctions())).append(')');

        sb.append(", #superfluousFunctions=").append(getNumSuperfluousFunctions());
        sb.append(" (").append(formatPercent(percentSuperfluousFunctions())).append(')');

        sb.append('}');
        return sb.toString();
    }
}
