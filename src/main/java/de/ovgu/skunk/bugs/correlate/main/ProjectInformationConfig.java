package de.ovgu.skunk.bugs.correlate.main;

import de.ovgu.skunk.commitanalysis.IHasSnapshotFilter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.PatternOptionBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Created by wfenske on 13.02.17.
 */
public class ProjectInformationConfig implements IHasSnapshotsDir, IHasResultsDir, IHasRevisionCsvFile, IHasProjectInfoFile, IHasProjectName, IHasSnapshotFilter {
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    public static final char OPT_HELP = 'h';
    public static final String OPT_HELP_L = "help";


    /**
     * Name of the project to analyze
     */
    public static final char OPT_PROJECT = 'p';

    /**
     * Long name of the {@link #OPT_PROJECT} option.
     */
    public static final String OPT_PROJECT_L = "project";

    /**
     * Directory containing the &lt;project&gt;ABRes/*.csv,
     * &lt;project&gt;AFRes/*.csv, &lt;project&gt;LFRes/*.csv files
     */
    public static final String OPT_RESULTS_DIR_L = "resultsdir";
    public static final String OPT_SNAPSHOTS_DIR_L = "snapshotsdir";


    /**
     * Name of the project to be analyzed, e.g., "openvpn"
     */
    protected String project;

    /**
     * Name of the results directory. Below this directory we expect a folder with the name of the project (see {@link
     * #project}), e.g. "results/openvpn".
     */
    protected String resultsDir = null;
    public static final String DEFAULT_RESULTS_DIR_NAME = "results";

    /**
     * Name of the revisionsFull.csv of this project, e.g.
     * "results/openvpn/revisionsFull.csv"
     */
    protected File revisionCsvFile = null;
    public static final String REVISIONS_FILE_BASENAME = "revisionsFull.csv";

    /**
     * Name of the snapshots directory of this project, e.g. "snapshots/openvpn"
     */
    protected String snapshotsDir = null;
    public static final String DEFAULT_SNAPSHOTS_DIR_NAME = "snapshots";

    /**
     * In case you don't want to analyze all snapshots of the project, but only some of them, their date strings will
     * be saved in this list.  In that case, the predicate {@link Optional#isPresent()} will return <code>true</code>.
     * Else, all snapshots should be analyzed.
     */
    protected Optional<List<Date>> snapshotFilter = Optional.empty();

    public static <TConfig extends IHasResultsDir & IHasProjectName> void parseProjectResultsDirFromCommandLine(CommandLine line, TConfig config) {
        final String resultsDirName;
        if (line.hasOption(OPT_RESULTS_DIR_L)) {
            resultsDirName = line.getOptionValue(OPT_RESULTS_DIR_L);
        } else {
            resultsDirName = DEFAULT_RESULTS_DIR_NAME;
        }
        final File resultsDir = new File(resultsDirName);
        if (!resultsDir.exists() || !resultsDir.isDirectory()) {
            throw new RuntimeException(
                    "The results directory does not exist or is not a directory: "
                            + resultsDir.getAbsolutePath());
        }
        final File projectResultsDir = new File(resultsDir, config.getProject());
        if (!projectResultsDir.exists() || !projectResultsDir.isDirectory()) {
            throw new RuntimeException(
                    "The project's results directory does not exist or is not a directory: "
                            + projectResultsDir.getAbsolutePath());
        }
        config.setResultsDir(resultsDir.getAbsolutePath());
    }

    public static <TConfig extends IHasSnapshotsDir & IHasProjectName> void parseSnapshotsDirFromCommandLine(CommandLine line, TConfig config) {
        parseSnapshotsDirFromCommandLine(line, config, SnapshotDirMissingStrategy.THROW_IF_MISSING);
    }

    public static <TConfig extends IHasSnapshotsDir & IHasProjectName> void parseSnapshotsDirFromCommandLine(CommandLine line, TConfig config, SnapshotDirMissingStrategy strategy) {
        final String snapshotsDirName;
        if (line.hasOption(OPT_SNAPSHOTS_DIR_L)) {
            snapshotsDirName = line.getOptionValue(OPT_SNAPSHOTS_DIR_L);
        } else {
            snapshotsDirName = DEFAULT_SNAPSHOTS_DIR_NAME;
        }
        final File snapshotsDir = new File(snapshotsDirName);
        final File projectSnapshotsDir = new File(snapshotsDir, config.getProject());

        if (projectSnapshotsDir.exists()) {
            if (!projectSnapshotsDir.isDirectory()) {
                throw new RuntimeException(
                        "The project's snapshots directory already exists but is not a directory: "
                                + projectSnapshotsDir.getAbsolutePath());
            }
        } else {
            // Snapshot dir does not exist
            strategy.handleMissingSnapshotDirectory(projectSnapshotsDir);
        }

        config.setSnapshotsDir(snapshotsDir.getAbsolutePath());
    }

    public static void parseSnapshotFilterDates(List<String> snapshotDateNames, IHasSnapshotFilter config) {
        List<Date> snapshotDates = new ArrayList<>(snapshotDateNames.size());
        final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

        for (String snapshotDateString : snapshotDateNames) {
            Date snapshotDate;
            try {
                snapshotDate = dateFormatter.parse(snapshotDateString);
            } catch (java.text.ParseException e) {
                throw new RuntimeException("Invalid snapshot (not in YYYY-MM-DD format): " + snapshotDateString, e);
            }
            snapshotDates.add(snapshotDate);
        }
        config.setSnapshotFilter(snapshotDates);
        config.validateSnapshotFilter();
    }

    public static void parseProjectNameFromCommandLine(CommandLine line, IHasProjectName config) {
        final String project = line.getOptionValue(OPT_PROJECT);
        config.setProject(project);
    }

    public static Option projectNameCommandLineOption(boolean required) {
        return Option.builder(String.valueOf(OPT_PROJECT)).longOpt(OPT_PROJECT_L)
                .desc("Name of the project to analyze. The project's data must be located in subdirectories of"
                        + " the results and snapshot directories.")
                .hasArg().argName("NAME").required(required).build();
    }

    public static Option helpCommandLineOption() {
        return Option.builder(String.valueOf(OPT_HELP)).longOpt(OPT_HELP_L)
                .desc("print this help screen and exit").build();
    }

    public static Option snapshotsDirCommandLineOption() {
        return Option.builder().longOpt(OPT_SNAPSHOTS_DIR_L)
                .desc("Directory where snapshots are located. The project's snapshots must be located in the "
                        + "<project> subdirectory within this directory, where <project>"
                        + " is the project name specified via the `--" + OPT_PROJECT_L + "' option." + " [Default="
                        + DEFAULT_SNAPSHOTS_DIR_NAME + "]")
                .hasArg().argName("DIR").type(PatternOptionBuilder.EXISTING_FILE_VALUE)
                // .required(required)
                .build();
    }

    public static Option resultsDirCommandLineOption() {
        return Option.builder().longOpt(OPT_RESULTS_DIR_L)
                .desc("Directory where to put results. The revisions CSV file, `" + REVISIONS_FILE_BASENAME
                        + "' must be located in the <project> subdirectory within this directory, where <project>"
                        + " is the project name specified via the `--" + OPT_PROJECT_L + "' option." + " [Default="
                        + DEFAULT_RESULTS_DIR_NAME + "]")
                .hasArg().argName("DIR").type(PatternOptionBuilder.EXISTING_FILE_VALUE)
                // .required(required)
                .build();
    }

    @Override
    public File projectResultsDir() {
        return new File(resultsDir, project);
    }

    @Override
    public File projectSnapshotsDir() {
        return new File(snapshotsDir, project);
    }

    @Override
    public File revisionCsvFile() {
        return revisionCsvFile;
    }

    @Override
    public File projectInfoFile() {
        return new File(projectResultsDir(), "projectInfo.csv");
    }

    @Override
    public void setResultsDir(String resultsDir) {
        this.resultsDir = resultsDir;
        initializeRevisionsCsvFileOrDie(resultsDir);
    }

    protected void initializeRevisionsCsvFileOrDie(String resultsDir) {
        File revisionsCsvFile = new File(projectResultsDir(), Config.REVISIONS_FILE_BASENAME);
        if (!revisionsCsvFile.exists() || revisionsCsvFile.isDirectory()) {
            throw new RuntimeException("The revisions CSV file does not exist or is a directory: "
                    + revisionsCsvFile.getAbsolutePath());
        }
        this.revisionCsvFile = revisionsCsvFile;
    }

    @Override
    public String getProject() {
        if (project == null) {
            throw new IllegalStateException("Attempt to read field `project' before initialization");
        }
        return project;
    }

    @Override
    public void setProject(String project) {
        if (project == null) {
            throw new IllegalArgumentException("`project' must not be null");
        }
        if ("".equals(project)) {
            throw new IllegalArgumentException("`project' must not be an empty string");
        }

        this.project = project;
    }

    @Override
    public void setSnapshotsDir(String snapshotsDir) {
        this.snapshotsDir = snapshotsDir;
    }

    @Override
    public File projectSnapshotDirForDate(Date date) {
        synchronized (dateFormatter) {
            return new File(projectSnapshotsDir(), dateFormatter.format(date));
        }
    }

    @Override
    public File snapshotResultsDirForDate(Date date) {
        synchronized (dateFormatter) {
            return new File(projectResultsDir(), dateFormatter.format(date));
        }
    }

    @Override
    public Optional<List<Date>> getSnapshotFilter() {
        return snapshotFilter;
    }

    @Override
    public void setSnapshotFilter(List<Date> snapshotFilter) {
        // The constructor function will perform the null check for us.
        this.snapshotFilter = Optional.of(snapshotFilter);
    }

    @Override
    public void validateSnapshotFilter() {
        Optional<List<Date>> filter = getSnapshotFilter();
        if (filter.isPresent()) {
            for (Date snapshotDate : filter.get()) {
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
