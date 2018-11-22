/**
 *
 */
package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.main.ProjectInformationConfig;

import java.io.File;
import java.util.Date;
import java.util.Optional;

/**
 * Runtime configuration options of this program
 *
 * @author wfenske
 */
public class ListChangedFunctionsConfig extends ProjectInformationConfig implements IHasRepoDir, IHasRepoAndResultsDir {

    public static final char OPT_THREADS = 't';
    public static final String OPT_THREADS_L = "threads";

    public static final char OPT_LIST_LEFTOVER_CHANGES = 'l';
    public static final String OPT_LIST_LEFTOVER_CHANGES_L = "list-leftover-changes";

    /**
     * <p>
     * Maximum size of binary files, in KB, to consider when analyzing commits. Since we don't particularly care about
     * binary files, we set this limit pretty low.
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
    public static final boolean DEFAULT_LEFT_OVER_CHANGES = false;
    private String repoDir = null;

    public int maxNumberOfFilesPerCommit = DEFAULT_MAX_NUMBER_OF_FILES_PER_COMMIT;
    public int maxSizeOfADiff = DEFAULT_MAX_SIZE_OF_A_DIFF;
    public int maxSizeOfDiffSource = DEFAULT_MAX_SIZE_OF_DIFF_SOURCE;
    public int binaryFileSizeThresholdInKb = DEFAULT_BINARY_FILE_SIZE_THRESHOLD_IN_KB;
    private int numThreads = DEFAULT_NUM_THREADS;
    private boolean listLeftOverChanges = DEFAULT_LEFT_OVER_CHANGES;

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

    public Optional<Date> getDummySnapshotDateToCoverRemainingChanges() {
        if (!this.getSnapshotFilter().isPresent()) {
            Date dummySnapshotDate = new Date(0);
            File dummySnapshotFile = new File(this.snapshotResultsDirForDate(dummySnapshotDate),
                    FunctionChangeHunksColumns.FILE_BASENAME);
            if (dummySnapshotFile.exists()) {
                return Optional.of(dummySnapshotDate);
            }
        }
        return Optional.empty();
    }

    public boolean isListLeftoverChanges() {
        return this.listLeftOverChanges;
    }

    public void setListLeftOverChanges(boolean listLeftOverChanges) {
        this.listLeftOverChanges = listLeftOverChanges;
    }
}
