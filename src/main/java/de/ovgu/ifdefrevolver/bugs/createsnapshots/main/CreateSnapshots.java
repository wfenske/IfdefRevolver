package de.ovgu.ifdefrevolver.bugs.createsnapshots.main;

import de.ovgu.ifdefrevolver.bugs.correlate.main.ProjectInformationConfig;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.ISnapshot;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.NullSnapshot;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.ProperSnapshot;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.Smell;
import de.ovgu.ifdefrevolver.bugs.minecommits.main.FindBugfixCommits;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

public class CreateSnapshots {

    private static Logger LOG = Logger.getLogger(CreateSnapshots.class);

    private CreateSnapshotsConfig conf;
    private int erroneousSnapshots = 0;

    /**
     * The main method.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        try {
            CreateSnapshots snapshotCreator = new CreateSnapshots();
            snapshotCreator.run(args);
            int errors = snapshotCreator.erroneousSnapshots;
            if (errors == 0) {
                LOG.info("Successfully processed all snapshots.");
            } else {
                LOG.warn("Error processing " + errors + " snapshot(s). See previous log messages for details.");
            }
            System.exit(errors);
        } catch (Exception e) {
            System.err.flush();
            System.out.flush();
            System.err.println("Error: " + e);
            e.printStackTrace();
            System.err.flush();
            System.out.flush();
            System.exit(1);
        }
    }

    private void run(String[] args) {
        this.conf = this.parseCommandLineArgs(args);
        final ISnapshotProcessingModeStrategy skunkStrategy = conf.skunkMode().getNewStrategyInstance(conf);
        applyStrategyToSnapshots(skunkStrategy);
    }

    private void applyStrategyToSnapshots(ISnapshotProcessingModeStrategy skunkStrategy) {
        this.erroneousSnapshots = 0;
        skunkStrategy.readAllRevisionsAndComputeSnapshots();
        skunkStrategy.removeOutputFiles();
        if (skunkStrategy.isCurrentSnapshotDependentOnPreviousSnapshot()) {
            processSnapshotsSequentially(skunkStrategy);
        } else {
            processSnapshotsInParallel(skunkStrategy);
        }
    }

    private void processSnapshotsInParallel(ISnapshotProcessingModeStrategy skunkStrategy) {
        Thread[] workers = createSnapshotProcessingWorkers(skunkStrategy);
        LOG.info(skunkStrategy.activityDisplayName() + " with " + workers.length + " threads.");
        executeSnapshotProcessingWorkers(workers);
    }

    private Thread[] createSnapshotProcessingWorkers(ISnapshotProcessingModeStrategy skunkStrategy) {
        Collection<ProperSnapshot> snapshotsToProcess = skunkStrategy.getSnapshotsToProcess();
        Iterator<ProperSnapshot> snapshotIterator = snapshotsToProcess.iterator();
        Thread[] workers = new Thread[conf.getNumberOfWorkerThreads()];

        final MutableInt progressCounter = new MutableInt(1);
        for (int i = 0; i < workers.length; i++) {
            Runnable r = newSkunkStrategyExecutorRunnable(skunkStrategy, snapshotIterator, progressCounter, snapshotsToProcess.size());
            workers[i] = new Thread(r);
        }
        return workers;
    }

    private void executeSnapshotProcessingWorkers(Thread[] workers) {
        for (int iWorker = 0; iWorker < workers.length; iWorker++) {
            workers[iWorker].start();
        }

        for (int iWorker = 0; iWorker < workers.length; iWorker++) {
            try {
                workers[iWorker].join();
            } catch (InterruptedException e) {
                LOG.warn("Interrupted while waiting for snapshot processing thread to finish.", e);
            }
        }
    }

    private void processSnapshotsSequentially(ISnapshotProcessingModeStrategy skunkStrategy) {
        final String activityDisplayName = skunkStrategy.activityDisplayName();
        LOG.info(activityDisplayName + " sequentially (no parallel processing possible).");

        ISnapshot previousSnapshot = NullSnapshot.getInstance();

        Collection<ProperSnapshot> snapshotsToProcess = skunkStrategy.getSnapshotsToProcess();
        final int totalNumberOfSnapshots = snapshotsToProcess.size();
        int myProgressPosition = 1;
        for (ProperSnapshot currentSnapshot : snapshotsToProcess) {
            LOG.info(skunkStrategy.activityDisplayName() + " on snapshot " +
                    myProgressPosition + "/" + totalNumberOfSnapshots + ": " + currentSnapshot);
            skunkStrategy.setPreviousSnapshot(previousSnapshot);
            if (skunkStrategy.snapshotAlreadyProcessed(currentSnapshot)) {
                LOG.info(skunkStrategy.activityDisplayName() + " skipped on " + currentSnapshot + ": snapshot already processed.");
            } else {
                skunkStrategy.ensureSnapshot(currentSnapshot);
                skunkStrategy.processSnapshot(currentSnapshot);
            }
            previousSnapshot = currentSnapshot;
            myProgressPosition++;
        }
    }

    private Runnable newSkunkStrategyExecutorRunnable(ISnapshotProcessingModeStrategy skunkStrategy,
                                                      Iterator<ProperSnapshot> snapshotIterator,
                                                      final MutableInt progressCounter,
                                                      final int totalNumberOfSnapshots) {
        return () -> {
            while (true) {
                final ProperSnapshot snapshot;
                final int myProgressPosition;
                synchronized (snapshotIterator) {
                    if (!snapshotIterator.hasNext()) {
                        break;
                    }
                    snapshot = snapshotIterator.next();
                }

                synchronized (progressCounter) {
                    myProgressPosition = progressCounter.getValue();
                    progressCounter.increment();
                }

                try {
                    LOG.info(skunkStrategy.activityDisplayName() + " on snapshot " +
                            myProgressPosition + "/" + totalNumberOfSnapshots + ": " + snapshot);
                    if (skunkStrategy.snapshotAlreadyProcessed(snapshot)) {
                        LOG.info(skunkStrategy.activityDisplayName() + " skipped on " + snapshot + ": snapshot already processed.");
                    } else {
                        skunkStrategy.ensureSnapshot(snapshot);
                        skunkStrategy.processSnapshot(snapshot);
                    }
                } catch (Throwable t) {
                    onSnapshotError(snapshot, t);
                }
            }
        };
    }

    synchronized void onSnapshotError(ProperSnapshot snapshot, Throwable t) {
        LOG.warn("Error processing " + snapshot, t);
        erroneousSnapshots++;
    }

    static class StreamReader implements Runnable {
        private final String progBasename;
        private final InputStream stream;
        private final String suffix;

        public StreamReader(InputStream stream, String progBasename, String suffix) {
            super();
            this.stream = stream;
            this.progBasename = progBasename;
            this.suffix = suffix;
        }

        @Override
        public void run() {
            final String logLinePrefix = "[" + progBasename + suffix + "] ";
            final Scanner scanner = new Scanner(stream);
            try {
                while (true) {
                    if (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        if (line.contains("ERROR ")) {
                            LOG.error(logLinePrefix + line.replaceFirst("^.*ERROR ", ""));
                        } else if (line.contains("WARN ")) {
                            LOG.warn(logLinePrefix + line.replaceFirst("^.*WARN ", ""));
                        } else if (line.contains("INFO ")) {
                            LOG.info(logLinePrefix + line.replaceFirst("^.*INFO ", ""));
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug(logLinePrefix + line.replaceFirst("^.*DEBUG ", "").replaceFirst("^.*TRACE ", ""));
                            }
                        }
                    } else {
                        break;
                    }
                }
            } finally {
                try {
                    scanner.close();
                } catch (Exception e) {
                    // Don't care
                }
            }
        }
    }

    static class ReaderCoordinator extends Thread {
        private final String progBasename;
        private final InputStream streamOut;
        private final InputStream streamErr;
        private volatile boolean interrupted = false;

        public ReaderCoordinator(String progBasename, InputStream streamOut, InputStream streamErr) {
            super("ReaderCoordinator for " + progBasename);
            this.progBasename = progBasename;
            this.streamOut = streamOut;
            this.streamErr = streamErr;
        }

        @Override
        public void run() {
            StreamReader readOut = new StreamReader(streamOut, progBasename, " out");
            StreamReader readErr = new StreamReader(streamErr, progBasename, " err");
            Thread threadOut = new Thread(readOut, "Stream reader for stdout of " + progBasename);
            Thread threadErr = new Thread(readErr, "Stream reader for stderr of " + progBasename);
            Thread[] threads = new Thread[]{threadOut, threadErr};
            for (Thread t : threads) {
                t.start();
            }
            try {
                boolean somethingWasAlive = true;
                while (somethingWasAlive && !interrupted) {
                    somethingWasAlive = false;
                    for (Thread t : threads) {
                        if (t.isAlive()) {
                            somethingWasAlive = true;
                            try {
                                synchronized (t) {
                                    t.wait(5);
                                }
                            } catch (InterruptedException e) {
                                // t probably called notify
                            }
                        }
                    }
                }
            } finally {
                for (Thread t : threads) {
                    try {
                        t.interrupt();
                    } catch (Exception e) {
                        // Don't care.
                        LOG.debug("Exception interrupting scanner thread " + t, e);
                    }
                }
            }
        }

        @Override
        public void interrupt() {
            this.interrupted = true;
            super.interrupt();
        }
    }

    static void runExternalCommand(final String prog, final File wd, final String... args) {
        final long startTime = System.currentTimeMillis();
        String[] processBuilderArgs = new String[args.length + 1];
        StringBuilder commandSb = new StringBuilder();
        processBuilderArgs[0] = prog;
        commandSb.append("`").append(prog);
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            processBuilderArgs[i + 1] = arg;
            commandSb.append(' ').append(arg);
        }
        commandSb.append("' in directory ").append(wd.getAbsolutePath());
        final String command = commandSb.toString();
        LOG.debug("Executing " + command + " ...");
        ProcessBuilder pb = new ProcessBuilder(processBuilderArgs);
        pb.directory(wd);
        // Scanner scanStdout = null;
        // Scanner scanStderr = null;
        Process p = null;
        InputStream processStdout = null;
        InputStream processStderr = null;
        int exitCode = -1;
        final String progBasename = new File(prog).getName();
        try {
            p = pb.start();
            processStdout = p.getInputStream();
            processStderr = p.getErrorStream();
            ReaderCoordinator readerCoordinator = new ReaderCoordinator(progBasename, processStdout, processStderr);
            readerCoordinator.start();
            // scanStdout = new Scanner(processStdout);
            // scanStderr = new Scanner(processStderr);
            // boolean hadMoreOutput;
            // do {
            // hadMoreOutput = false;
            // if (scanStdout.hasNextLine()) {
            // hadMoreOutput = true;
            // LOG.info("[" + progBasename + " out] " + scanStdout.nextLine());
            // }
            // if (scanStderr.hasNextLine()) {
            // hadMoreOutput = true;
            // LOG.info("[" + progBasename + " err] " + scanStderr.nextLine());
            // }
            // } while (hadMoreOutput);
            try {
                exitCode = p.waitFor();
                // Wait for some last output
                if (readerCoordinator.isAlive()) {
                    synchronized (readerCoordinator) {
                        try {
                            readerCoordinator.wait(50);
                        } catch (InterruptedException e) {
                            // Not important
                        }
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Got interrupted while executing " + command, e);
            } finally {
                try {
                    p.destroy();
                } finally {
                    try {
                        readerCoordinator.interrupt();
                        readerCoordinator.join();
                    } catch (Exception e) {
                        LOG.debug("Exception interrupting process reader coordinator", e);
                        // Don't care.
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error executing " + command, e);
        } finally {
            // try {
            // if (scanStdout != null)
            // scanStdout.close();
            // } catch (Exception e) {
            // /* ignored */
            // }
            try {
                if (processStdout != null) processStdout.close();
            } catch (Exception e) {
                /* ignored */
            }
            // try {
            // if (scanStderr != null)
            // scanStderr.close();
            // } catch (Exception e) {
            // /* ignored */
            // }
            try {
                if (processStderr != null) processStderr.close();
            } catch (Exception e) {
                /* ignored */
            }
        }
        if (exitCode != 0) {
            throw new RuntimeException("Command " + command + " exited with non-zero exit code " + exitCode);
        }
        long timeForProg = System.currentTimeMillis() - startTime;
        LOG.debug("Executing " + command + " took " + timeForProg + " ms");
    }

    static String pathRelativeTo(File file, File dir) {
        File canonicalDir = null;
        try {
            canonicalDir = dir.getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to determine canonical name of directory " + dir.getAbsolutePath(), e);
        }
        final int lenDirName = canonicalDir.getAbsolutePath().length();
        File fCanon;
        try {
            fCanon = file.getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to determine canonical name of file " + file.getAbsolutePath(), e);
        }
        String canonicalFileName = fCanon.getAbsolutePath();
        return canonicalFileName.substring(lenDirName + 1);
    }

    private static final char OPT_HELP = 'h';
    // mutex groupof options controlling how Skunk is called
    private static final String OPT_CHECKOUT_L = "checkout";
    private static final String OPT_PREPROCESS_L = "preprocess";
    /**
     * --detect=AB|AF|LF
     */
    private static final String OPT_DETECT_L = "detect";

    /**
     * Size of a commit window, requires positive integer argument
     */
    private static final String OPT_COMMIT_WINDOW_SIZE_L = "windowsize";
    private static final char OPT_COMMIT_WINDOW_SIZE = 's';

    /**
     * How the size of a commit window is counted.  Requires an arg, as determined by the values in {@link
     * CommitWindowSizeMode}
     */
    private static final String OPT_COMMIT_WINDOW_SIZE_MODE_L = "sizemode";

    /**
     * --reposdir=, e.g. /home/me/Repositories/
     */
    private static final String OPT_REPOS_DIR_L = "reposdir";
    /**
     * Directory where the smell configuration files reside, e.g., <code>AnnotationBundle.csm</code>,
     * <code>AnnotationFile.csm</code>, <code>LargeFeature.csm</code>
     */
    private static final String OPT_SMELL_CONFIGS_DIR_L = "smellconfigsdir";

    /**
     * Analyze input to decide what to do during runtime
     *
     * @param args the command line arguments
     */
    private CreateSnapshotsConfig parseCommandLineArgs(String[] args) {
        CreateSnapshotsConfig res = new CreateSnapshotsConfig();
        CommandLineParser parser = new DefaultParser();
        Options fakeOptionsForHelp = makeOptions(true);
        Options actualOptions = makeOptions(false);
        CommandLine line;
        try {
            CommandLine dummyLine = parser.parse(fakeOptionsForHelp, args);
            if (dummyLine.hasOption('h')) {
                HelpFormatter formatter = new HelpFormatter();
                System.err.flush();
                formatter.printHelp(progName() + " [OPTIONS]... [SNAPSHOTS]...",
                        "Create snapshots of a VCS repository and detect variability-aware smells in those snapshots using Skunk and cppstats.\n\t" +
                                "Snapshot creation requires information about the commits to this repository, which can be obtained by running " +
                                FindBugfixCommits.class.getSimpleName() + " on the repository.\n\t" +
                                "The snapshots will be created and an extensive Skunk analysis performed when this program is run with the `--" + OPT_CHECKOUT_L
                                + "' option. Subsequent runs with the `"
                                + OPT_PREPROCESS_L + "' and `"
                                + OPT_DETECT_L + "' options will reuse the snapshots and Skunk analysis data and proceed much faster."
                                + "\n\n"
                                + "If you want to process only some snapshots of the project, you may optionally name the snapshots"
                                + " to process by specifying multiple snapshot dates in YYYY-MM-DD format.  This is only possible if the `"
                                + OPT_PREPROCESS_L + "' or `" + OPT_DETECT_L + "' options is present."
                                + "\n\nOptions:\n",
                        actualOptions, null, false);
                System.out.flush();
                System.exit(0);
                // We never actually get here due to the preceding
                // System.exit(int) call.
                return null;
            }
            line = parser.parse(actualOptions, args);
        } catch (ParseException e) {
            System.err.println("Error in command line: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printUsage(new PrintWriter(System.err, true), 80, progName(), actualOptions);
            System.exit(1);
            // We never actually get here due to the preceding System.exit(int)
            // call.
            return null;
        }

        if (line.hasOption(OPT_CHECKOUT_L)) {
            res.setSnapshotProcessingMode(SnapshotProcessingMode.CHECKOUT);
        } else if (line.hasOption(OPT_PREPROCESS_L)) {
            res.setSnapshotProcessingMode(SnapshotProcessingMode.PREPROCESS);
        } else if (line.hasOption(OPT_DETECT_L)) {
            res.setSnapshotProcessingMode(SnapshotProcessingMode.DETECTSMELLS);
            parseSmellDetectionArgs(res, line);
        } else {
            throw new RuntimeException(
                    "Either `--" + OPT_CHECKOUT_L + "', `--" + OPT_PREPROCESS_L + "' or `--" + OPT_DETECT_L + "' must be specified!");
        }

        parseCommitWindowSizeModeFromCommandLine(res, line);
        parseCommitWindowSizeFromCommandLine(res, line, res.commitWindowSizeMode().defaultSize());

        ProjectInformationConfig.parseProjectNameFromCommandLine(line, res);
        ProjectInformationConfig.parseSnapshotsDirFromCommandLine(line, res, res.skunkMode().snapshotDirMissingStrategy());
        ProjectInformationConfig.parseProjectResultsDirFromCommandLine(line, res);

        final String reposDirName;
        if (line.hasOption(OPT_REPOS_DIR_L)) {
            reposDirName = line.getOptionValue(OPT_REPOS_DIR_L);
        } else {
            reposDirName = CreateSnapshotsConfig.DEFAULT_REPOS_DIR_NAME;
        }

        File reposDir = new File(reposDirName);
        if (!reposDir.exists() || !reposDir.isDirectory()) {
            throw new RuntimeException(
                    "The repository directory does not exist or is not a directory: " + reposDir.getAbsolutePath());
        }
        res.setReposDir(reposDir.getAbsolutePath());

        List<String> snapshotDateNames = line.getArgList();
        if (!snapshotDateNames.isEmpty()) {
            if (res.skunkMode() == SnapshotProcessingMode.CHECKOUT) {
                throw new RuntimeException("Processing individual snapshots is not supported in"
                        + " `--" + OPT_CHECKOUT_L + "' mode.");
            }

            ProjectInformationConfig.parseSnapshotFilterDates(snapshotDateNames, res);
        }

        return res;
    }

    private void parseCommitWindowSizeModeFromCommandLine(CreateSnapshotsConfig res, CommandLine line) {
        if (line.hasOption(OPT_COMMIT_WINDOW_SIZE_MODE_L)) {
            if (res.skunkMode() != SnapshotProcessingMode.CHECKOUT) {
                LOG.warn("Ignoring commit window size mode because `--" + OPT_CHECKOUT_L + "' was not specified.");
            } else {
                CommitWindowSizeMode mode = parseCommitWindowSizeModeValueOrDie(line);
                res.setCommitWindowSizeMode(mode);
            }
        }
    }

    private CommitWindowSizeMode parseCommitWindowSizeModeValueOrDie(CommandLine line) {
        String modeName = line.getOptionValue(OPT_COMMIT_WINDOW_SIZE_MODE_L);
        try {
            return CommitWindowSizeMode.valueOf(modeName.toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new RuntimeException("Invalid value for option `--" + OPT_COMMIT_WINDOW_SIZE_MODE_L
                    + "': Expected: " + getCommitWindowSizeModeArgs() + " got: " + modeName);
        }
    }

    private void parseCommitWindowSizeFromCommandLine(CreateSnapshotsConfig res, CommandLine line, int defaultValue) {
        if (line.hasOption(OPT_COMMIT_WINDOW_SIZE)) {
            if (res.skunkMode() != SnapshotProcessingMode.CHECKOUT) {
                LOG.warn("Ignoring custom commit window size because `--" + OPT_CHECKOUT_L + "' was not specified.");
            } else {
                int windowSizeNum = parseCommitWindowSizeValueOrDie(line);
                res.setCommitWindowSize(windowSizeNum);
            }
        } else {
            res.setCommitWindowSize(defaultValue);
        }
    }

    private int parseCommitWindowSizeValueOrDie(CommandLine line) {
        final String windowSizeString = line.getOptionValue(OPT_COMMIT_WINDOW_SIZE);
        int windowSizeNum;
        try {
            windowSizeNum = Integer.valueOf(windowSizeString);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid value for option `--" + OPT_COMMIT_WINDOW_SIZE_L
                    + "': Not a valid integer: " + windowSizeString);
        }
        if (windowSizeNum < 1) {
            throw new RuntimeException("Invalid value for option `--" + OPT_COMMIT_WINDOW_SIZE_L
                    + "': Commit window size must be an integer >= 1.");
        }
        return windowSizeNum;
    }

    private void parseSmellDetectionArgs(CreateSnapshotsConfig res, CommandLine line) {
        String smellShortName = line.getOptionValue(OPT_DETECT_L);
        try {
            res.setSmell(Smell.valueOf(smellShortName));
        } catch (IllegalArgumentException e) {
            StringBuilder sb = new StringBuilder();
            for (Smell m : Smell.values()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(m.name());
            }
            throw new RuntimeException("Illegal value for option --" + OPT_DETECT_L + ": " + smellShortName
                    + ". Valid values are " + sb.toString());
        }

        File smellConfigsDir = parseSmellConfigsDirFromCommandLineOrDie(line);
        setSmellConfigFileOrDie(res, smellConfigsDir);
    }

    private void setSmellConfigFileOrDie(CreateSnapshotsConfig res, File smellConfigsDir) {
        File smellConfigFile = new File(smellConfigsDir, res.getSmell().configFileName);
        if (smellConfigFile.exists() && !smellConfigFile.isDirectory()) {
            res.setSmellConfig(smellConfigFile.getAbsolutePath());
        } else {
            throw new RuntimeException("The smell detection configuration file does not exist or is a directory: "
                    + smellConfigFile.getAbsolutePath());
        }
    }

    private File parseSmellConfigsDirFromCommandLineOrDie(CommandLine line) {
        final String smellConfigsDirName;
        if (line.hasOption(OPT_SMELL_CONFIGS_DIR_L)) {
            smellConfigsDirName = line.getOptionValue(OPT_SMELL_CONFIGS_DIR_L);
        } else {
            smellConfigsDirName = CreateSnapshotsConfig.DEFAULT_SMELL_CONFIGS_DIR_NAME;
        }

        File smellConfigsDir = new File(smellConfigsDirName);
        if (!smellConfigsDir.isDirectory()) {
            throw new RuntimeException("Smell configurations directory does not exist or is not a directory: "
                    + smellConfigsDir.getAbsolutePath());
        }
        return smellConfigsDir;
    }

    private Options makeOptions(boolean forHelp) {
        boolean required = !forHelp;
        Options options = new Options();
        // @formatter:off
        // --help= option


        options.addOption(ProjectInformationConfig.helpCommandLineOption());

        options.addOption(ProjectInformationConfig.projectNameCommandLineOption(required));
        options.addOption(ProjectInformationConfig.snapshotsDirCommandLineOption());
        options.addOption(ProjectInformationConfig.resultsDirCommandLineOption());

        options.addOption(
                Option.builder().longOpt(OPT_SMELL_CONFIGS_DIR_L)
                        .desc("Name of the directory holding the smell detection configuration files for "
                                + "Skunk. [Default=" + CreateSnapshotsConfig.DEFAULT_SMELL_CONFIGS_DIR_NAME + "]")
                        .hasArg().argName("DIR").build());

        options.addOption(Option.builder().longOpt(OPT_REPOS_DIR_L)
                .desc("Directory below which the repository of the project (specified via `--" + ProjectInformationConfig.OPT_PROJECT_L
                        + "') can be found." + " [Default=" + CreateSnapshotsConfig.DEFAULT_REPOS_DIR_NAME + "]")
                .hasArg().argName("DIR").build());

        options.addOption(Option.builder().longOpt(OPT_COMMIT_WINDOW_SIZE_MODE_L)
                .desc("How the size of the commit windows is determined. This option is only relevant during" +
                        " snapshot creation, i.e., when running in `--" + OPT_CHECKOUT_L
                        + "' mode." + " [Default=" + CreateSnapshotsConfig.DEFAULT_COMMIT_WINDOW_SIZE_MODE.name().toLowerCase() + "]")
                .hasArg().argName(getCommitWindowSizeModeArgs()).build());


        options.addOption(Option.builder(String.valueOf(OPT_COMMIT_WINDOW_SIZE)).longOpt(OPT_COMMIT_WINDOW_SIZE_L)
                .desc("Size of a commit window, specified as a positive integer. This option is only relevant during" +
                        " snapshot creation, i.e., when running in `--" + OPT_CHECKOUT_L
                        + "' mode." + " [Default value depends on the mode specified via `--" +
                        OPT_COMMIT_WINDOW_SIZE_MODE_L + "': " + getCommitWindowSizeDefaults() + "]")
                .hasArg().argName("NUM").build());

        // --checkout, --preprocess and --detect options
        OptionGroup skunkModeOptions = new OptionGroup();
        skunkModeOptions.setRequired(required);

        skunkModeOptions.addOption(Option.builder().longOpt(OPT_CHECKOUT_L)
                .desc("Create snapshots from the specified repository and convert them to srcML using cppstats.")
                .build());
        skunkModeOptions.addOption(Option.builder().longOpt(OPT_PREPROCESS_L)
                .desc("Preprocess cppstats files using Skunk.  Requires a previous run of this"
                        + " tool with the `--" + OPT_CHECKOUT_L + "' option on.")
                .build());
        skunkModeOptions.addOption(Option.builder().longOpt(OPT_DETECT_L)
                .desc("Detect smells using on already preprocessed data saved during a previous run of this"
                        + " tool with the `--" + OPT_PREPROCESS_L + "' option on.")
                .hasArg()
                .argName(getValidSmellArgs())
                .build());

        options.addOptionGroup(skunkModeOptions);
        // @formatter:on
        return options;
    }

    private String getCommitWindowSizeDefaults() {
        StringBuilder result = new StringBuilder();
        for (CommitWindowSizeMode m : CommitWindowSizeMode.values()) {
            if (result.length() > 0) {
                result.append(", ");
            }
            String name = m.name().toLowerCase();
            result.append(m.defaultSize()).append(" for `").append(name).append("'");
        }
        return result.toString();
    }

    private static String getValidSmellArgs() {
        StringBuilder validSmellArgs = new StringBuilder();
        for (Smell m : Smell.values()) {
            if (validSmellArgs.length() > 0) {
                validSmellArgs.append("|");
            }
            validSmellArgs.append(m.name());
        }
        return validSmellArgs.toString();
    }

    private static String getCommitWindowSizeModeArgs() {
        StringBuilder result = new StringBuilder();
        for (CommitWindowSizeMode m : CommitWindowSizeMode.values()) {
            if (result.length() > 0) {
                result.append("|");
            }
            result.append(m.name().toLowerCase());
        }
        return result.toString();
    }

    private String progName() {
        return this.getClass().getSimpleName();
    }
}
