/**
 * 
 */
package de.ovgu.skunk.bugs.eval.data;

import java.util.Comparator;

/**
 * <p>
 * Information about Features, used for detecting large features.
 * </p>
 * 
 * <p>
 * Feature implements the {@link Comparable} interface. The default comparison
 * compares by feature name.
 * </p>
 * 
 * @author wfenske
 */
public class Feature implements Comparable<Feature> {
    private final String name;
    private final int linesOfFeatureCode;
    private final int numberOfOccurrences;
    private final int numberOfCompilationUnits;
    private final double lgScore;

    /**
     * Compares by (feature) lines of code, in ascending order
     * 
     * @see #getLinesOfFeatureCode()
     */
    public static final Comparator<Feature> LOC_COMP = new Comparator<Feature>() {
        @Override
        public int compare(Feature a, Feature b) {
            return a.linesOfFeatureCode - b.linesOfFeatureCode;
        }
    };

    /**
     * Compares by number of occurrences, in ascending order
     * 
     * @see #getNumberOfOccurrences()
     */
    public static final Comparator<Feature> NUM_OCCURRENCES_COMP = new Comparator<Feature>() {
        @Override
        public int compare(Feature a, Feature b) {
            return a.numberOfOccurrences - b.numberOfOccurrences;
        }
    };

    /**
     * Compares by number of compilation units where this feature occurs, in
     * ascending order
     * 
     * @see #getNumberOfCompilationUnits()
     */
    public static final Comparator<Feature> NUM_COMPILATION_UNITS = new Comparator<Feature>() {
        @Override
        public int compare(Feature a, Feature b) {
            return a.numberOfCompilationUnits - b.numberOfCompilationUnits;
        }
    };

    /**
     * @param name
     *            Name of the feature
     * @param linesOfFeatureCode
     *            a.k.a. LOFC
     * @param numberOfOccurrences
     *            number of <code>#ifdef</code> that reference this feature,
     *            a.k.a. NOFC
     * @param numberOfCompilationUnits
     *            a.k.a. NOCU
     * @param lgScore
     *            Smell score as computed by Skunk
     */
    public Feature(String name, int linesOfFeatureCode, int numberOfOccurrences, int numberOfCompilationUnits,
            double lgScore) {
        this.name = name;
        this.linesOfFeatureCode = linesOfFeatureCode;
        this.numberOfOccurrences = numberOfOccurrences;
        this.numberOfCompilationUnits = numberOfCompilationUnits;
        this.lgScore = lgScore;
    }

    /**
     * @return Name of the feature/feature constant
     */
    public String getName() {
        return name;
    }

    /**
     * @return LOFC
     */
    public int getLinesOfFeatureCode() {
        return linesOfFeatureCode;
    }

    /**
     * @return number of <code>#ifdef</code> that reference this feature a.k.a.
     *         NOFC
     */
    public int getNumberOfOccurrences() {
        return numberOfOccurrences;
    }

    /**
     * @return NOCU
     */
    public int getNumberOfCompilationUnits() {
        return numberOfCompilationUnits;
    }

    /**
     * @return LGScore smell value, as computed by Skunk
     */
    public double getLgScore() {
        return lgScore;
    }

    @Override
    public int compareTo(Feature other) {
        return this.name.compareTo(other.name);
    }

    /*
     * #toString() generated by Eclipse
     */
    @Override
    public String toString() {
        return String.format("Feature [name=%s, LOC=%s, NOFC=%s, NOCU=%s, lgScore=%s]", name, linesOfFeatureCode,
                numberOfOccurrences, numberOfCompilationUnits, lgScore);
    }
}
