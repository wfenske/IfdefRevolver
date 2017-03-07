/**
 *
 */
package de.ovgu.skunk.commitanalysis;

import de.ovgu.skunk.bugs.correlate.main.ProjectInformationConfig;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Runtime configuration options of this program
 *
 * @author wfenske
 */
public class ListChangedFunctionsConfig extends ProjectInformationConfig implements IHasRepoDir, IHasSnapshots {

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
    private String repoDir = null;

    /**
     * In case you don't want to analyze all snapshots of the project, but only some of them, their date strings will
     * be saved in this list.  In that case, the predicate {@link Optional#isPresent()} will return <code>true</code>.
     * Else, all snapshots should be analyzed.
     */
    private Optional<List<Date>> snapshots = Optional.empty();

    /**
     * The IDs of GIT commit which should be analyzed
     */
    //public List<String> commitIds;
    public int maxNumberOfFilesPerCommit = DEFAULT_MAX_NUMBER_OF_FILES_PER_COMMIT;
    public int maxSizeOfADiff = DEFAULT_MAX_SIZE_OF_A_DIFF;
    public int maxSizeOfDiffSource = DEFAULT_MAX_SIZE_OF_DIFF_SOURCE;
    public int binaryFileSizeThresholdInKb = DEFAULT_BINARY_FILE_SIZE_THRESHOLD_IN_KB;
    private int numThreads = DEFAULT_NUM_THREADS;

    @Override
    public void validateRepoDir() {
        File repoFile = new File(getRepoDir());
        if (!repoFile.exists()) {
            throw new IllegalArgumentException("Repository directory does not exist: " + getRepoDir());
        }
        if (!repoFile.isDirectory()) {
            throw new IllegalArgumentException("Repository is not a directory: " + getRepoDir());
        }
    }

    public int getNumThreads() {
        return numThreads;
    }

    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    @Override
    public String getRepoDir() {
        return repoDir;
    }

    @Override
    public void setRepoDir(String repoDir) {
        this.repoDir = repoDir;
    }

    @Override
    public Optional<List<Date>> getSnapshots() {
        return snapshots;
    }

    @Override
    public void setSnapshots(List<Date> snapshots) {
        // The constructor function will perform the null check for us.
        this.snapshots = Optional.of(snapshots);
    }

    @Override
    public void validateSnapshots() {
        if (this.snapshots.isPresent()) {
            for (Date snapshotDate : this.snapshots.get()) {
                File snapshotDir = snapshotResultsDirForDate(snapshotDate);
                if (!snapshotDir.exists()) {
                    throw new IllegalArgumentException("Snapshot directory does not exist: " + snapshotDir);
                }
                if (!snapshotDir.isDirectory()) {
                    throw new IllegalArgumentException("Snapshot directory is not a directory: " + snapshotDir);
                }
            }
        }
    }


}
