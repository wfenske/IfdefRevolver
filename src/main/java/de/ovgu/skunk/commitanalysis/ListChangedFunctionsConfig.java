/**
 *
 */
package de.ovgu.skunk.commitanalysis;

import de.ovgu.skunk.bugs.correlate.main.ProjectInformationConfig;

import java.io.File;

/**
 * Runtime configuration options of this program
 *
 * @author wfenske
 */
public class ListChangedFunctionsConfig extends ProjectInformationConfig {
    public static final char OPT_REPO = 'r';
    public static final String OPT_REPO_L = "repo";

    public static final char OPT_THREADS = 't';
    public static final String OPT_THREADS_L = "threads";

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
    public static final String DEFAULT_REPOS_DIR_NAME = "repos";
    public static final int DEFAULT_NUM_THREADS = 4;
    public String repoDir = null;
    /**
     * The IDs of GIT commit which should be analyzed
     */
    //public List<String> commitIds;
    public int maxNumberOfFilesPerCommit = DEFAULT_MAX_NUMBER_OF_FILES_PER_COMMIT;
    public int maxSizeOfADiff = DEFAULT_MAX_SIZE_OF_A_DIFF;
    public int maxSizeOfDiffSource = DEFAULT_MAX_SIZE_OF_DIFF_SOURCE;
    public int binaryFileSizeThresholdInKb = DEFAULT_BINARY_FILE_SIZE_THRESHOLD_IN_KB;
    private int numThreads = DEFAULT_NUM_THREADS;

    public void validateRepoDir() {
        File repoFile = new File(repoDir);
        if (!repoFile.exists()) {
            throw new IllegalArgumentException("Repository directory does not exist: " + repoDir);
        }
        if (!repoFile.isDirectory()) {
            throw new IllegalArgumentException("Repository is not a directory: " + repoDir);
        }
    }

    public int getNumThreads() {
        return numThreads;
    }

    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }
}
