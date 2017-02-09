/**
 *
 */
package de.ovgu.skunk.bugs.correlate.main;

import org.apache.log4j.Logger;

import java.io.File;

/**
 * @author wfenske
 */
public class Config {

    @SuppressWarnings("unused")
    private static Logger log = Logger.getLogger(Config.class);

    /**
     * Name of the project to be analyzed, e.g., "openvpn"
     */
    String project;

    /**
     * Name of the snapshots directory of this project, e.g. "results/openvpn"
     */
    String resultsDir = null;
    public static final String DEFAULT_RESULTS_DIR_NAME = "results";

    /**
     * Name of the revisionsFull.csv of this project, e.g.
     * "results/openvpn/revisionsFull.csv"
     */
    String revisionCsvFile = null;
    public static final String REVISIONS_FILE_BASENAME = "revisionsFull.csv";

    /**
     * Name of the snapshots directory of this project, e.g. "snapshots/openvpn"
     */
    String snapshotsDir = null;
    public static final String DEFAULT_SNAPSHOTS_DIR_NAME = "snapshots";

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

    public String getResultsDir() {
        return resultsDir;
    }

    public String getSnapshotsDir() {
        return this.snapshotsDir;
    }

	/*
     * private static Path createNewTempDir() { Path result; String tmpDirStr =
	 * System.getProperty(TMP_DIR_PROP_NAME); if (tmpDirStr == null ||
	 * tmpDirStr.isEmpty()) { throw new RuntimeException("Java property " +
	 * TMP_DIR_PROP_NAME + " is not set."); } try { result =
	 * Files.createTempDirectory(TMP_DIR_PROP_NAME); } catch (IOException e) {
	 * throw new RuntimeException(
	 * "Failed to create temporary directory in system temp dir " +
	 * TMP_DIR_PROP_NAME, e); } log.debug("Created temporary directory " +
	 * result.toString()); return result; }
	 */

    public String getRevisionCsvFile() {
        return revisionCsvFile;
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
        return new File(getResultsDir(), "smellOverview" + smell.name() + ".csv");
    }

    public File corOverviewFile() {
        return new File(getResultsDir(), "corOverview.csv");
    }

    public File corOverviewSizeFile() {
        return new File(getResultsDir(), "corOverviewSize.csv");
    }

    public File correlatedResultsDir() {
        return new File(getResultsDir(), "Correlated");
    }

    public File projectInfoFile() {
        return new File(getResultsDir(), "projectInfo.csv");
    }

    public File projectAnalysisFile() {
        return new File(getResultsDir(), "projectAnalysis.csv");
    }
}
