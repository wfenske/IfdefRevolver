package de.ovgu.skunk.bugs.eval.data;

import de.ovgu.skunk.bugs.eval.output.SmellCSV;

/**
 * Columns of the large feature detection CSV file written during preprocessing
 * 
 * @author wfenske
 * @see SmellCSV#processLargeFeatureXmlFile(java.io.File, java.io.File,
 *      java.io.File)
 */
public enum LargeFeatureCsvColumns {
	// @formatter:off
	/** Column holding the name of file participating in feature */
	FILE_NAME /* 0 */,
	/**
	 * Column holding the &quot;large feature score&quot; (computed by Skunk) of
	 * the feature
	 */
	FEAT_LG_SCORE /* 1 */,
	/** Column holding the name of the feature */
	FEAT_NAME /* 2 */,
	/** Column holding the lines of code encompassed by the feature */
	FEAT_LOC /* 3 */,
	/**
	 * Column holding (roughly) the number of <code>#ifdef</code>s referencing
	 * the feature
	 */
	FEAT_OCCURRENCES /* 4 */,
	/** Column holding the number of files that participate in the feature */
	FEAT_FILES /* 5 */;
	// @formatter:on
}
