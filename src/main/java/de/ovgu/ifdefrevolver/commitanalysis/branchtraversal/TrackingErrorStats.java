package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TrackingErrorStats {
    private final Set<FunctionId> actualFunctions;
    private final Set<FunctionId> computedFunctions;
    private Set<FunctionId> matchingFunctions;
    private Set<FunctionId> missingFunctions;
    private Set<FunctionId> superfluousFunctions;

    public TrackingErrorStats(Set<FunctionId> actualFunctions, Set<FunctionId> computedFunctions) {
        this.actualFunctions = actualFunctions;
        this.computedFunctions = computedFunctions;

        this.matchingFunctions = new HashSet<>(actualFunctions);
        matchingFunctions.retainAll(computedFunctions);

        this.missingFunctions = new HashSet<>(actualFunctions);
        missingFunctions.removeAll(computedFunctions);

        this.superfluousFunctions = new HashSet<>(computedFunctions);
        superfluousFunctions.removeAll(actualFunctions);
    }

    public static TrackingErrorStats aggregate(Collection<TrackingErrorStats> stats) {
        int ixTrackerId = 0;
        Set<FunctionId> allActualFunctions = new HashSet<>();
        Set<FunctionId> allComputedFunctions = new HashSet<>();
        for (TrackingErrorStats s : stats) {
            String prefix = String.format("#%03d ", ixTrackerId);
            allActualFunctions.addAll(addPrefix(prefix, s.actualFunctions));
            allComputedFunctions.addAll(addPrefix(prefix, s.computedFunctions));
            ixTrackerId++;
        }
        return new TrackingErrorStats(allActualFunctions, allComputedFunctions);
    }

    private static Collection<? extends FunctionId> addPrefix(String prefix, Set<FunctionId> functionIds) {
        return functionIds.stream().map(f -> addPrefix(prefix, f)).collect(Collectors.toSet());
    }

    private static FunctionId addPrefix(String prefix, FunctionId id) {
        return new FunctionId(prefix + id.signature, prefix + id.file);
    }

    public boolean isMissing(FunctionId id) {
        return this.missingFunctions.contains(id);
    }

    public Set<FunctionId> getMissingFunctions() {
        return missingFunctions;
    }

    public Set<FunctionId> getSuperfluousFunctions() {
        return superfluousFunctions;
    }

    public int getNumActualFunctions() {
        return actualFunctions.size();
    }

    public int getNumMissingFunctions() {
        return missingFunctions.size();
    }

    public int getNumSuperfluousFunctions() {
        return superfluousFunctions.size();
    }

    public int getNumMatchingFunctions() {
        return matchingFunctions.size();
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
