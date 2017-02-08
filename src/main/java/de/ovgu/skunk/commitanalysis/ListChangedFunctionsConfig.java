/**
 *
 */
package de.ovgu.skunk.commitanalysis;

import java.io.File;
import java.util.List;

/**
 * Runtime configuration options of this program
 *
 * @author wfenske
 */
public class ListChangedFunctionsConfig {
    public static final char OPT_HELP = 'h';
    public static final String OPT_HELP_L = "help";
    public static final char OPT_REPO = 'r';
    public static final String OPT_REPO_L = "repo";
    /**
     * <p>
     * Maximum size of binary files, in KB, to consider when analyzing commits.
     * Since we don't particularly care about binary files, we set this limit
     * pretty low.
     * </p>
     * <p>
     * 1024 means 1 mb max a file.
     * </p>
     */
    public static final int DEFAULT_BINARY_FILE_SIZE_THRESHOLD_IN_KB = 1024;
    public static final int DEFAULT_MAX_NUMBER_OF_FILES_PER_COMMIT = 200;
    public static final int DEFAULT_MAX_SIZE_OF_A_DIFF = 1024 * 1024;
    public static final int DEFAULT_MAX_SIZE_OF_DIFF_SOURCE = 1024 * 1024;
    public String repoDir = null;
    /**
     * The IDs of GIT commit which should be analyzed
     */
    public List<String> commitIds;
    public int maxNumberOfFilesPerCommit = DEFAULT_MAX_NUMBER_OF_FILES_PER_COMMIT;
    public int maxSizeOfADiff = DEFAULT_MAX_SIZE_OF_A_DIFF;
    public int maxSizeOfDiffSource = DEFAULT_MAX_SIZE_OF_DIFF_SOURCE;
    public int binaryFileSizeThresholdInKb = DEFAULT_BINARY_FILE_SIZE_THRESHOLD_IN_KB;

    public void validateRepoDir() {
        File repoFile = new File(repoDir);
        if (!repoFile.exists()) {
            throw new IllegalArgumentException("Repository directory does not exist: " + repoDir);
        }
        if (!repoFile.isDirectory()) {
            throw new IllegalArgumentException("Repository is not a directory: " + repoDir);
        }
    }
}
