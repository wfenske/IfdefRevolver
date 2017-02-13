/**
 *
 */
package de.ovgu.skunk.bugs.correlate.main;

import org.apache.log4j.Logger;

import java.io.File;

/**
 * @author wfenske
 */
public class Config extends ProjectInformationConfig {

    @SuppressWarnings("unused")
    private static Logger log = Logger.getLogger(Config.class);

    private double smellScoreThreshold = 0;
    private double smellyFilesFraction = 0.5; // used to be 0.7
    double largeFileSizePercentage = 25.0;

    double largeFeatureLocPercentage = 2.5;
    double largeFeatureOccurrencePercentage = 0;
    double largeFeatureNumCompilationUnitsPercentage = 0;

    /**
     * @return Percentage of features, regarding LOC, to be considered large.
     * Value between 0.0 and 100.0.
     */
    public double getLargeFeatureLocPercentage() {
        return largeFeatureLocPercentage;
    }

    /**
     * @return Percentage of features, regarding references via
     * <code>#ifdef</code>s, to be considered large. Value between 0.0
     * and 100.0.
     */
    public double getLargeFeatureOccurrencePercentage() {
        return largeFeatureOccurrencePercentage;
    }

    /**
     * @return Percentage of features, regarding files in which they are used,
     * to be considered large. Value between 0.0 and 100.0.
     */
    public double getLargeFeatureNumCompilationUnitsPercentage() {
        return largeFeatureNumCompilationUnitsPercentage;
    }

    public Config() {
        // Just for traceability
    }

    // public String getSmellDir() {
    // return smellDir;
    // }

    /**
     * @return fraction of best smells, score-wise, are considered actually
     * smelly
     */
    public double getSmellyFilesFraction() {
        return smellyFilesFraction;
    }

    /**
     * @return Large files must belong to this percentage (LOC-wise) to be
     * considered large. Values lie between 0.0 and 100.0
     */
    public double getLargeFileSizePercentage() {
        return largeFileSizePercentage;
    }

    public double getSmellScoreThreshold() {
        return smellScoreThreshold;
    }

    public File smellOverviewFile(Smell smell) {
        return new File(projectResultsDir(), "smellOverview" + smell.name() + ".csv");
    }

    public File corOverviewFile() {
        return new File(projectResultsDir(), "corOverview.csv");
    }

    public File corOverviewSizeFile() {
        return new File(projectResultsDir(), "corOverviewSize.csv");
    }

    public File correlatedResultsDir() {
        return new File(projectResultsDir(), "Correlated");
    }

    public File projectAnalysisFile() {
        return new File(projectResultsDir(), "projectAnalysis.csv");
    }
}
