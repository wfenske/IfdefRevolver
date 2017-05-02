package de.ovgu.ifdefrevolver.bugs.createsnapshots.main;

import de.ovgu.ifdefrevolver.bugs.correlate.main.ProjectInformationConfig;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.Smell;

import java.io.File;
import java.util.Optional;

/**
 * Created by wfenske on 06.04.17.
 */
public class CreateSnapshotsConfig extends ProjectInformationConfig {
    public static final String GIT_PROG = "git";
    public static final String CPP_SKUNK_PROG = "cppSkunk.sh";
    public static final String SKUNK_PROG = "skunk.sh";

    public void setReposDir(String reposDir) {
        this.reposDir = reposDir;
    }

    private String reposDir = null;

    public static final String DEFAULT_REPOS_DIR_NAME = "repos";

    private String smellConfig = null;
    public static final String DEFAULT_SMELL_CONFIGS_DIR_NAME = "smellconfigs";

    private SnapshotProcessingMode snapshotProcessingMode = null;
    private Smell smell = null;

    private int commitWindowSize = -1;
    public static final CommitWindowSizeMode DEFAULT_COMMIT_WINDOW_SIZE_MODE = CommitWindowSizeMode.COMMITS;
    private CommitWindowSizeMode commitWindowSizeMode = DEFAULT_COMMIT_WINDOW_SIZE_MODE;

    private Optional<Integer> numberOfWorkerThreads = Optional.empty();

    public String smellModeFile() {
        return getSmell().fileName;
    }

    public File projectRepoDir() {
        return new File(reposDir, getProject());
    }

    public CommitWindowSizeMode commitWindowSizeMode() {
        return this.commitWindowSizeMode;
    }

    public void setCommitWindowSize(int commitWindowSize) {
        this.commitWindowSize = commitWindowSize;
    }

    public File projectInfoCsv() {
        return new File(projectResultsDir(), "projectInfo.csv");
    }

    public File projectAnalysisCsv() {
        return new File(projectResultsDir(), "projectAnalysis.csv");
    }

    public int commitWindowSize() {
        return commitWindowSize;
    }

    public void setCommitWindowSizeMode(CommitWindowSizeMode commitWindowSizeMode) {
        this.commitWindowSizeMode = commitWindowSizeMode;
    }

    public SnapshotProcessingMode skunkMode() {
        return snapshotProcessingMode;
    }

    public void setSnapshotProcessingMode(SnapshotProcessingMode snapshotProcessingMode) {
        this.snapshotProcessingMode = snapshotProcessingMode;
        if (!numberOfWorkerThreads.isPresent()) {
            numberOfWorkerThreads = Optional.of(snapshotProcessingMode.defaultNumberOfWorkerThreads());
        }
    }

    public Smell getSmell() {
        return smell;
    }

    public void setSmell(Smell smell) {
        this.smell = smell;
    }

    public String smellConfig() {
        return this.smellConfig;
    }

    public void setSmellConfig(String smellConfig) {
        this.smellConfig = smellConfig;
    }

    public int getNumberOfWorkerThreads() {
        return numberOfWorkerThreads.get();
    }
}
