package de.ovgu.ifdefrevolver.bugs.createsnapshots.main;

import de.ovgu.ifdefrevolver.bugs.correlate.main.ProjectInformationConfig;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.Smell;
import de.ovgu.ifdefrevolver.commitanalysis.IHasRepoDir;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import java.io.File;
import java.util.Optional;

/**
 * Created by wfenske on 06.04.17.
 */
public class CreateSnapshotsConfig extends ProjectInformationConfig implements IHasRepoDir {
    public static final String GIT_PROG = "git";
    public static final String CPP_SKUNK_PROG = "cppSkunk.sh";
    public static final String SKUNK_PROG = "skunk.sh";
    private String repoDir;

    /**
     * Whether existing results should be overwritten
     */
    public static final char OPT_FORCE = 'f';

    /**
     * Long name of the {@link #OPT_FORCE} option.
     */
    public static final String OPT_FORCE_L = "force";

    /**
     * Whether to continue an interrupted checkout
     */
    public static final char OPT_CONTINUE = 'c';

    /**
     * Long name of the {@link #OPT_CONTINUE} option.
     */
    public static final String OPT_CONTINUE_L = "continue";


    // mutex groupof options controlling how Skunk is called
    public static final String OPT_CHECKOUT_L = "checkout";
    public static final String OPT_PREPROCESS_L = "preprocess";
    /**
     * --detect=AB|AF|LF
     */
    public static final String OPT_DETECT_L = "detect";

    /**
     * Size of a commit window, requires positive integer argument
     */
    public static final String OPT_SNAPSHOT_SIZE_L = "snapshotsize";
    public static final char OPT_SNAPSHOT_SIZE = 's';

    private String smellConfig = null;
    public static final String DEFAULT_SMELL_CONFIGS_DIR_NAME = "smellconfigs";

    private SnapshotProcessingMode snapshotProcessingMode = null;
    private Smell smell = null;

    private boolean force = false;
    private boolean continueCheckout = false;

    private int snapshotSize = -1;
    public static final SnapshotSizeMode DEFAULT_COMMIT_WINDOW_SIZE_MODE = SnapshotSizeMode.COMMITS;
    private SnapshotSizeMode snapshotSizeMode = DEFAULT_COMMIT_WINDOW_SIZE_MODE;

    private Optional<Integer> numberOfWorkerThreads = Optional.empty();

    public String smellModeFile() {
        return getSmell().fileName;
    }

//    public File projectRepoDir() {
//        return new File(reposDir, getProject());
//    }

    public SnapshotSizeMode commitWindowSizeMode() {
        return this.snapshotSizeMode;
    }

    public void setSnapshotSize(int snapshotSize) {
        this.snapshotSize = snapshotSize;
    }

    public File projectInfoCsv() {
        return new File(projectResultsDir(), "projectInfo.csv");
    }

    public File projectAnalysisCsv() {
        return new File(projectResultsDir(), "projectAnalysis.csv");
    }

    public int getSnapshotSize() {
        return snapshotSize;
    }

    public void setSnapshotSizeMode(SnapshotSizeMode snapshotSizeMode) {
        this.snapshotSizeMode = snapshotSizeMode;
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

    public static Option forceCommandLineOption() {
        return Option.builder(String.valueOf(OPT_FORCE)).longOpt(OPT_FORCE_L)
                .desc("Overwrite and/or delete files created by a previous run of the tool before recreating them. (requires option `--" +
                        OPT_CHECKOUT_L + "'; conflicts with option `--" + OPT_CONTINUE_L + "')")
                .build();
    }

    public static Option continueCommandLineOption() {
        return Option.builder(String.valueOf(OPT_CONTINUE)).longOpt(OPT_CONTINUE_L)
                .desc("Continue a checkout from by a previous, interrupted run of the tool. (requires option `--" +
                        OPT_CHECKOUT_L + "'; " + "conflicts with option `--" + OPT_FORCE_L + "')")
                .build();
    }

    public static void parseForceFromCommandLine(CommandLine line, CreateSnapshotsConfig config) {
        if (line.hasOption(OPT_FORCE)) {
            config.setForce(true);
        }
    }

    public static void parseContinueFromCommandLine(CommandLine line, CreateSnapshotsConfig config) {
        if (line.hasOption(OPT_CONTINUE)) {
            config.setContinueCheckout(true);
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

    @Override
    public void validateRepoDir() {
        ProjectInformationConfig.validateRepoDir(getRepoDir());
    }

    @Override
    public String getRepoDir() {
        return repoDir;
    }

    @Override
    public void setRepoDir(String repoDir) {
        this.repoDir = repoDir;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isForce() {
        return force;
    }

    public void setContinueCheckout(boolean continueCheckout) {
        this.continueCheckout = continueCheckout;
    }

    public boolean isContinueCheckout() {
        return continueCheckout;
    }
}
