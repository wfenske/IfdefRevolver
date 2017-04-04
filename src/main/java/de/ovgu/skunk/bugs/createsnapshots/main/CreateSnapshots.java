package de.ovgu.skunk.bugs.createsnapshots.main;

import de.ovgu.skunk.bugs.correlate.main.ProjectInformationConfig;
import de.ovgu.skunk.bugs.createsnapshots.data.Commit;
import de.ovgu.skunk.bugs.createsnapshots.data.ISnapshot;
import de.ovgu.skunk.bugs.createsnapshots.data.NullSnapshot;
import de.ovgu.skunk.bugs.createsnapshots.data.ProperSnapshot;
import de.ovgu.skunk.bugs.createsnapshots.input.FileFinder;
import de.ovgu.skunk.bugs.createsnapshots.input.RevisionsCsvReader;
import de.ovgu.skunk.bugs.minecommits.main.FindBugfixCommits;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;

public class CreateSnapshots {
    private static final String GIT_PROG = "git";
    private static final String CPP_SKUNK_PROG = "cppSkunk.sh";
    private static final String SKUNK_PROG = "skunk.sh";
    private static final String CPPSTATS_INPUT_TXT = "cppstats_input.txt";
    private static Logger LOG = Logger.getLogger(CreateSnapshots.class);

    public static class Config extends ProjectInformationConfig {
        /**
         * Number of bug-fixes that a commit window should contain
         */
        //public static final int DEFAULT_COMMIT_WINDOW_SIZE_IN_NUMBER_OF_BUGFIXES = 100;
        public static final int DEFAULT_COMMIT_WINDOW_SIZE = 200;

        private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
        private String reposDir = null;

        public static final String DEFAULT_REPOS_DIR_NAME = "repos";

        private String smellConfig = null;
        public static final String DEFAULT_SMELL_CONFIGS_DIR_NAME = "smellconfigs";

        public SkunkMode skunkMode = null;
        public Smell smell = null;
        public boolean optimized = false;

        /**
         * Number of commits that make up a commit window
         */
        private int commitWindowSize = DEFAULT_COMMIT_WINDOW_SIZE;

        public String smellModeFile() {
            return smell.fileName;
        }

        public File projectRepoDir() {
            return new File(reposDir, getProject());
        }

        @Override
        public File projectResultsDir() {
            return new File(resultsDir, getProject());
        }

        @Override
        public File projectSnapshotsDir() {
            return new File(snapshotsDir, getProject());
        }

        //public int commitWindowSizeInNumberOfBugfixes() {
        //    return DEFAULT_COMMIT_WINDOW_SIZE_IN_NUMBER_OF_BUGFIXES;
        //}
        public int commitWindowSize() {
            return commitWindowSize;
        }

        public void setCommitWindowSize(int commitWindowSize) {
            this.commitWindowSize = commitWindowSize;
        }

        public synchronized File tmpSnapshotDir(Date snapshotDate) {
            return new File(projectSnapshotsDir(), dateFormatter.format(snapshotDate));
        }

        public synchronized File resultsSnapshotDir(Date snapshotDate) {
            return new File(projectResultsDir(), dateFormatter.format(snapshotDate));
        }

        public File projectInfoCsv() {
            return new File(projectResultsDir(), "projectInfo.csv");
        }

        public File projectAnalysisCsv() {
            return new File(projectResultsDir(), "projectAnalysis.csv");
        }
    }

    private Config conf;
    private int erroneousSnapshots = 0;

    /**
     * Enumerates possible smells, such as {@link #AB} (for Annotation Bundle),
     * or {@link #LF} (for Large File).
     *
     * @author wfenske
     */
    public enum Smell {
        // @formatter:off
        AB("functions.csv", "AnnotationBundle.csm"),
        AF("files.csv", "AnnotationFile.csm"),
        LF("features.csv", "LargeFeature.csm");
        // @formatter:on
        public final String fileName;
        public final String configFileName;

        private Smell(String fileName, String configFileName) {
            this.fileName = fileName;
            this.configFileName = configFileName;
        }
    }

    public enum SkunkMode {
        CHECKOUT {
            @Override
            public ISkunkModeStrategy getNewStrategyInstance(Config conf) {
                return new CheckoutStrategy(conf);
            }
        },

        PREPROCESS {
            @Override
            public ISkunkModeStrategy getNewStrategyInstance(Config conf) {
                return new PreprocessStrategy(conf);
            }
        },

        DETECTSMELLS {
            @Override
            public ISkunkModeStrategy getNewStrategyInstance(Config conf) {
                return new DetectSmellsStrategy(conf);
            }
        };

        public abstract ISkunkModeStrategy getNewStrategyInstance(Config conf);
    }

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

    interface ISkunkModeStrategy {
        /**
         * Delete output files, such as the projectInfo.csv, if they are
         * recreated anyway
         */
        void removeOutputFiles();

        /**
         * Create the Snapshot, if it does not already exist. Snapshots are
         * stored on disk in the folder {@link Config#projectSnapshotsDir()}
         * /&lt;date&gt;, where &lt;date&gt; hsa the format
         * &quot;YYYY-MM-DD&quot;.
         *
         * @param currentSnapshot Start date of the current snapshot
         */
        void ensureSnapshot(ProperSnapshot currentSnapshot);

        /**
         * Run cppstats (if necessary) and Skunk
         *
         * @param currentSnapshot The current snapshot
         */
        void processSnapshot(ProperSnapshot currentSnapshot);

        /**
         * @return String describing in human-readable form what this strategy is about to do.
         */
        String activityDisplayName();

        boolean isCurrentSnapshotDependentOnPreviousSnapshot();

        void setPreviousSnapshot(ISnapshot previousSnapshot);
    }

    static class CheckoutStrategy implements ISkunkModeStrategy {
        private final Config conf;
        private ISnapshot previousSnapshot;

        public CheckoutStrategy(Config conf) {
            this.conf = conf;
        }

        @Override
        public void removeOutputFiles() {
            File projInfoCsv = conf.projectInfoCsv();
            if (projInfoCsv.exists()) {
                LOG.warn("Running in CHECKOUT mode, but project info CSV already exists. It will be overwritten: "
                        + projInfoCsv.getAbsolutePath());
                projInfoCsv.delete();
            }
            File projAnalysisCsv = conf.projectAnalysisCsv();
            if (projAnalysisCsv.exists()) {
                LOG.warn("Running in CHECKOUT mode, but project analysis CSV already exists. It will be overwritten: "
                        + projAnalysisCsv.getAbsolutePath());
                projAnalysisCsv.delete();
            }
        }

        @Override
        public boolean isCurrentSnapshotDependentOnPreviousSnapshot() {
            return true;
        }

        @Override
        public void setPreviousSnapshot(ISnapshot previousSnapshot) {
            this.previousSnapshot = previousSnapshot;
        }

        @Override
        public void ensureSnapshot(ProperSnapshot currentSnapshot) {
            // GIT CHECKOUT
            gitCheckout(currentSnapshot);
            // Anzahl der .c Dateien checken
            LOG.debug("Looking for checkout .c files in directory " + conf.projectRepoDir().getAbsolutePath());
            List<File> filesFound = FileFinder.find(conf.projectRepoDir(), "(.*\\.c$)");
            final int filesCount = filesFound.size();
            LOG.info(String.format("Found %d .c file%s in %s", filesCount, filesCount == 1 ? "" : "s",
                    conf.projectRepoDir().getAbsolutePath()));
            conf.projectResultsDir().mkdirs();
            appendToProjectCsv(currentSnapshot, filesFound);
            appendToProjectAnalysisCsv(currentSnapshot, filesFound);
            final File curSnapshotDir = conf.tmpSnapshotDir(currentSnapshot.revisionDate());
            curSnapshotDir.mkdirs();
            copyCheckoutToTmpSnapshotDir(filesFound, currentSnapshot);
            writeCppstatsConfigFile(curSnapshotDir);
        }

        /**
         * Git Checkout Script Aufruf
         *
         * @param snapshot
         */
        private void gitCheckout(ProperSnapshot snapshot) {
            String hash = snapshot.revisionHash();
            runExternalCommand(GIT_PROG, conf.projectRepoDir().getAbsoluteFile(), "checkout", "--force", hash);
        }

        private void appendToProjectAnalysisCsv(ProperSnapshot snapshot, List<File> filesFound) {
            final File csvOutFile = conf.projectAnalysisCsv();
            FileWriter fileWriter = null;
            BufferedWriter buff = null;
            try {
                fileWriter = new FileWriter(csvOutFile, true);
                buff = new BufferedWriter(fileWriter);
                for (File f : filesFound) {
                    String relativeFileName = pathRelativeTo(f, conf.projectRepoDir());
                    buff.write(relativeFileName + "," + snapshot.revisionDateString());
                    buff.newLine();
                }
            } catch (IOException e1) {
                throw new RuntimeException("Error writing stuff to " + csvOutFile.getAbsolutePath(), e1);
            } finally {
                closeBufferedWriter(buff, fileWriter);
            }
        }

        private void appendToProjectCsv(final ProperSnapshot snapshot, List<File> filesFound) {
            BufferedWriter buff = null;
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(conf.projectInfoCsv(), true);
                buff = new BufferedWriter(fileWriter);
                buff.write(snapshot.revisionHash() + "," + snapshot.revisionDateString() + "," + filesFound.size());
                buff.newLine();
                LOG.debug("Added snapshot " + snapshot.revisionDateString() + " to "
                        + conf.projectInfoCsv().getAbsolutePath());
            } catch (IOException e1) {
                throw new RuntimeException("Error appending snapshot " + snapshot.revisionDateString() + " to "
                        + conf.projectInfoCsv().getAbsolutePath(), e1);
            } finally {
                closeBufferedWriter(buff, fileWriter);
            }
        }

        private void closeBufferedWriter(BufferedWriter buff, FileWriter fileWriter) {
            if (buff != null) {
                try {
                    buff.close();
                } catch (IOException e) {
                    // We don't care.
                }
            } else {
                if (fileWriter != null) {
                    try {
                        fileWriter.close();
                    } catch (IOException e) {
                        // We don't care.
                    }
                }
            }
        }

        private void copyCheckoutToTmpSnapshotDir(List<File> filesInCurrentCheckout, ProperSnapshot currentSnapshot) {
            final File cppstatsDir = new File(conf.tmpSnapshotDir(currentSnapshot.revisionDate()), "source");
            // Copy Files
            if (conf.optimized) {
                Collection<File> changedFiles = previousSnapshot.computeChangedFiles(filesInCurrentCheckout,
                        currentSnapshot);
                copyAllFiles(changedFiles, cppstatsDir);
            } else {
                copyAllFiles(filesInCurrentCheckout, cppstatsDir);
            }
        }

        /**
         * Copies all files for the current Snapshot
         *
         * @param filesInCurrentCheckout
         * @param destDir
         */
        private void copyAllFiles(Collection<File> filesInCurrentCheckout, File destDir) {
            for (File file : filesInCurrentCheckout) {
                copyFileToDir(file, destDir);
            }
        }

        private void copyFileToDir(File file, File destDir) {
            String relFileName = pathRelativeTo(file, conf.projectRepoDir());
            File targetFileName = new File(destDir, relFileName);
            final File targetFileDir = targetFileName.getParentFile();
            targetFileDir.mkdirs();
            try {
                Files.copy(file.toPath(), targetFileName.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Error copying file " + file.getAbsolutePath() + " to " + targetFileName.getAbsolutePath(), e);
            }
        }

        /**
         * Create the cppstats_input.txt in the given directory
         *
         * @param snapshotDir Directory where the files of the snapshot will be put
         */
        private void writeCppstatsConfigFile(final File snapshotDir) {
            File cppstatsConfigFile = new File(snapshotDir, CPPSTATS_INPUT_TXT);
            PrintWriter writer = null;
            try {
                // File cppstatsConfigFile = new File(conf.projectTmpDir(),
                // CPPSTATS_INPUT_TXT);
                writer = new PrintWriter(cppstatsConfigFile);
                writer.write(snapshotDir.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException("Error creating " + CPPSTATS_INPUT_TXT + " in " + snapshotDir, e);
            } finally {
                if (writer != null) writer.close();
            }
            LOG.info("Wrote " + cppstatsConfigFile.getAbsolutePath());
        }

        @Override
        public void processSnapshot(ProperSnapshot currentSnapshot) {
            final Date snapshotDate = currentSnapshot.revisionDate();
            final File resultsSnapshotDir = conf.resultsSnapshotDir(snapshotDate);
            final File tmpSnapshotDir = conf.tmpSnapshotDir(snapshotDate);
            resultsSnapshotDir.mkdirs();
            List<String> args = new ArrayList<>();
            //args.add(conf.smellConfig /* ARG1 */);
            //args.add(resultsSnapshotDir.getAbsolutePath() /* ARG2 */);
            final Date prevSnapshotDate = previousSnapshot.revisionDate();
            if (prevSnapshotDate != null) {
                final File prevSnapshotDir = conf.tmpSnapshotDir(prevSnapshotDate);
                final File prevCppstatsInputList = new File(prevSnapshotDir, CPPSTATS_INPUT_TXT);
                args.add("--prepareFrom=" + prevCppstatsInputList.getAbsolutePath());
            } else {
                args.add("--lazyPreparation");
            }
            runExternalCommand(CPP_SKUNK_PROG, tmpSnapshotDir /* WD */, args.toArray(new String[args.size()]));
            saveSnapshotCommitsHashes(currentSnapshot);
        }

        @Override
        public String activityDisplayName() {
            return "Checking out sources and running cppstats";
        }

        private void saveSnapshotCommitsHashes(ProperSnapshot snapshot) {
            File projSnapshotMetadataDir = new File(conf.projectResultsDir(), "snapshots");
            if (!projSnapshotMetadataDir.isDirectory()) {
                projSnapshotMetadataDir.mkdir();
            }
            File out = new File(projSnapshotMetadataDir, snapshot.revisionDateString() + ".csv");
            BufferedWriter buff = null;
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(out);
                buff = new BufferedWriter(fileWriter);
                buff.write(snapshot.getSortIndex() + "," + snapshot.revisionDateString());
                buff.newLine();
                for (Commit commit : snapshot.getCommits().keySet()) {
                    buff.write(commit.getHash());
                    buff.newLine();
                }
                LOG.info("Stored commit hashes of snapshot " + snapshot + " to " + out.getAbsolutePath());
            } catch (IOException e1) {
                throw new RuntimeException("Error appending snapshot " + snapshot.revisionDateString() + " to "
                        + conf.projectInfoCsv().getAbsolutePath(), e1);
            } finally {
                closeBufferedWriter(buff, fileWriter);
            }
        }

    }

    /**
     * Only call Skunk to create its intermediate files, without doing anything else.  Requires cppstats to already have run.
     */
    static class PreprocessStrategy implements ISkunkModeStrategy {
        private final Config conf;

        public PreprocessStrategy(Config conf) {
            this.conf = conf;
        }

        @Override
        public void removeOutputFiles() {
            // Nothing to do.
        }

        @Override
        public boolean isCurrentSnapshotDependentOnPreviousSnapshot() {
            return false;
        }

        @Override
        public void setPreviousSnapshot(ISnapshot previousSnapshot) {
            // Since we don't depend on the previous snapshot, there is nothing to do here.
        }

        @Override
        public void ensureSnapshot(ProperSnapshot currentSnapshot) {
            // The snapshot has already been created in a previous run in CHECKOUT
            // mode --> Nothing to do.
        }

        @Override
        public void processSnapshot(ProperSnapshot currentSnapshot) {
            Date snapshotDate = currentSnapshot.revisionDate();
            File workingDir = conf.resultsSnapshotDir(snapshotDate);
            File snapshotDir = conf.tmpSnapshotDir(snapshotDate);
            if (!workingDir.isDirectory()) {
                boolean success = workingDir.mkdirs();
                if (!success) {
                    throw new RuntimeException("Error creating directory or one of its parents: " + workingDir);
                }
            }
            runExternalCommand(SKUNK_PROG, workingDir, "--source=" + snapshotDir.getAbsolutePath(), "--save-intermediate");
        }

        @Override
        public String activityDisplayName() {
            return "Preprocessing cppstats sources with Skunk";
        }
    }

    static class DetectSmellsStrategy implements ISkunkModeStrategy {
        private final Config conf;

        public DetectSmellsStrategy(Config conf) {
            this.conf = conf;
        }

        @Override
        public void removeOutputFiles() {
            // Nothing to do.
        }

        @Override
        public boolean isCurrentSnapshotDependentOnPreviousSnapshot() {
            return false;
        }

        @Override
        public void setPreviousSnapshot(ISnapshot previousSnapshot) {
            // Since we don't depend on the previous snapshot, there is nothing to do here.
        }

        @Override
        public void ensureSnapshot(ProperSnapshot currentSnapshot) {
            // The snapshot has already been created in a previous run in CHECKOUT
            // mode --> Nothing to do.
        }

        @Override
        public void processSnapshot(ProperSnapshot currentSnapshot) {
            Date snapshotDate = currentSnapshot.revisionDate();
            File resultsDir = conf.resultsSnapshotDir(snapshotDate);
            runExternalCommand(SKUNK_PROG, resultsDir, "--processed=.", "--config=" + conf.smellConfig);
            moveSnapshotSmellDetectionResults(currentSnapshot);
        }

        @Override
        public String activityDisplayName() {
            return "Detecting smell " + conf.smell;
        }

        private void moveSnapshotSmellDetectionResults(ProperSnapshot curSnapshot) {
            File sourcePath = conf.resultsSnapshotDir(curSnapshot.startDate());
            File smellResultsDir = new File(conf.projectResultsDir(), conf.smell.name() + "Res");
            smellResultsDir.mkdirs(); // Create target directory
            moveSnapshotSmellDetectionResults(curSnapshot, sourcePath, smellResultsDir);
        }

        private void moveSnapshotSmellDetectionResults(ProperSnapshot snapshot, File sourcePath, File smellResultsDir) {
            final String snapshotDateString = snapshot.revisionDateString();
            List<File> filesFindCSV = FileFinder.find(sourcePath, "(.*\\.csv$)");
            // Rename and move CSV files (smell severity)
            for (File f : filesFindCSV) {
                String fileName = f.getName();
                if (fileName.equals("skunk_metrics_" + conf.smellModeFile())) {
                    final File copyFrom = new File(f.getParentFile(),
                            conf.smell.name() + "Res" + ".csv");
                    f.renameTo(copyFrom);
                    File copyTo = new File(smellResultsDir, snapshotDateString + ".csv");
                    try {
                        Files.copy(copyFrom.toPath(), copyTo.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException("Error copying files from " + copyFrom.getAbsolutePath() + " to "
                                + copyTo.getAbsolutePath(), e);
                    }
                } else {
                    //f.delete();
                }
            }

            // Rename and move XML files (smell location)
            if (conf.smell == Smell.LF) {
                List<File> filesFindXML = FileFinder.find(sourcePath, "(.*\\.xml$)");
                for (File f : filesFindXML) {
                    if (f.getName().contains(conf.smellModeFile())) {
                        File copyTo = new File(smellResultsDir, snapshotDateString + ".xml");
                        try {
                            Files.copy(f.toPath(), copyTo.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException(
                                    "Error copying file " + f.getAbsolutePath() + " to " + copyTo.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }
    }

    private void run(String[] args) {
        this.conf = this.parseCommandLineArgs(args);
        final ISkunkModeStrategy skunkStrategy = conf.skunkMode.getNewStrategyInstance(conf);

        revisionsReader = new RevisionsCsvReader(conf.revisionCsvFile(), conf.commitWindowSize());
        revisionsReader.readAllRevisionsAndComputeSnapshots();
        applyStrategyToSnapshots(skunkStrategy);
    }

    private void applyStrategyToSnapshots(ISkunkModeStrategy skunkStrategy) {
        this.erroneousSnapshots = 0;
        skunkStrategy.removeOutputFiles();
        if (skunkStrategy.isCurrentSnapshotDependentOnPreviousSnapshot()) {
            processSnapshotsSequentially(skunkStrategy);
        } else {
            processSnapshotsInParallel(skunkStrategy);
        }
    }

    private void processSnapshotsInParallel(ISkunkModeStrategy skunkStrategy) {
        Thread[] workers = createSnapshotProcessingWorkers(skunkStrategy);
        executeSnapshotProcessingWorkers(workers);
    }

    private Thread[] createSnapshotProcessingWorkers(ISkunkModeStrategy skunkStrategy) {
        Collection<ProperSnapshot> snapshotsToProcess = revisionsReader.getSnapshotsFiltered(conf);
        Iterator<ProperSnapshot> snapshotIterator = snapshotsToProcess.iterator();
        final int NUM_THREADS = 4;
        Thread[] workers = new Thread[NUM_THREADS];

        for (int i = 0; i < workers.length; i++) {
            Runnable r = newSkunkStrategyExecutorRunnable(skunkStrategy, snapshotIterator);
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

    private void processSnapshotsSequentially(ISkunkModeStrategy skunkStrategy) {
        final String activityDisplayName = skunkStrategy.activityDisplayName();
        ISnapshot previousSnapshot = NullSnapshot.getInstance();

        if (conf.getSnapshotFilter().isPresent()) {
            throw new RuntimeException("Snapshot filter cannot be used in conjunction with " + conf.skunkMode);
        }

        for (ProperSnapshot currentSnapshot : revisionsReader.getSnapshots()) {
            LOG.info(activityDisplayName + " " + currentSnapshot);
            skunkStrategy.setPreviousSnapshot(previousSnapshot);
            skunkStrategy.ensureSnapshot(currentSnapshot);
            skunkStrategy.processSnapshot(currentSnapshot);
            previousSnapshot = currentSnapshot;
        }
    }

    private Runnable newSkunkStrategyExecutorRunnable(ISkunkModeStrategy skunkStrategy,
                                                      Iterator<ProperSnapshot> snapshotIterator) {
        return () -> {
            while (true) {
                final ProperSnapshot snapshot;
                synchronized (snapshotIterator) {
                    if (!snapshotIterator.hasNext()) {
                        break;
                    }
                    snapshot = snapshotIterator.next();
                }
                LOG.info(skunkStrategy.activityDisplayName() + " " + snapshot);
                try {
                    skunkStrategy.ensureSnapshot(snapshot);
                    skunkStrategy.processSnapshot(snapshot);
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
            Scanner scanner = new Scanner(stream);
            try {
                while (true) {
                    if (scanner.hasNextLine()) {
                        LOG.debug("[" + progBasename + suffix + "] " + scanner.nextLine());
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
     * Optional flag for optimizing some unknown magical stuff
     */
    private static final String OPT_OPTIMIZED = "O";
    /**
     * --reposdir=, e.g. /home/hnes/Masterarbeit/Repositories/
     */
    private static final String OPT_REPOS_DIR_L = "reposdir";
    /**
     * e.g., /home/hnes/Masterarbeit/SmellConfigs/
     */
    private static final String OPT_SMELL_CONFIGS_DIR_L = "smellconfigsdir";
    // private static final String OPT_CPPSTATS_PATH =
    // "/home/hnes/Masterarbeit/Tools/cppstats/";
    /**
     * e.g., /home/hnes/Masterarbeit/Results/
     */
    private static final String OPT_RESULTS_DIR_L = "resultsdir";
    /**
     * /home/hnes/Masterarbeit/Temp/
     */
    private static final String OPT_SNAPSHOTS_DIR_L = "snapshotsdir";
    /**
     * Specifies project name, e.g., openvpn
     */
    private static final char OPT_PROJECT = 'p';
    /**
     * Long name of the {@link #OPT_PROJECT} option.
     */
    private static final String OPT_PROJECT_L = "project";
    private RevisionsCsvReader revisionsReader = null;

    // private static final String OPT_;

    /**
     * Analyze input to decide what to do during runtime
     *
     * @param args the command line arguments
     */
    private Config parseCommandLineArgs(String[] args) {
        Config res = new Config();
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
            res.skunkMode = SkunkMode.CHECKOUT;
        } else if (line.hasOption(OPT_PREPROCESS_L)) {
            res.skunkMode = SkunkMode.PREPROCESS;
        } else if (line.hasOption(OPT_DETECT_L)) {
            res.skunkMode = SkunkMode.DETECTSMELLS;
            parseSmellDetectionArgs(res, line);
        } else {
            throw new RuntimeException(
                    "Either `--" + OPT_CHECKOUT_L + "', `--" + OPT_PREPROCESS_L + "' or `--" + OPT_DETECT_L + "' must be specified!");
        }

        if (line.hasOption(OPT_COMMIT_WINDOW_SIZE)) {
            if (res.skunkMode != SkunkMode.CHECKOUT) {
                LOG.warn("Ignoring custom commit window size because `--" + OPT_CHECKOUT_L + "' was not specified.");
            } else {
                int windowSizeNum = parseCommitWindowSizeOrDie(line);
                res.setCommitWindowSize(windowSizeNum);
            }
        }

        ProjectInformationConfig.parseProjectNameFromCommandLine(line, res);
        ProjectInformationConfig.parseSnapshotsDirFromCommandLine(line, res);
        ProjectInformationConfig.parseProjectResultsDirFromCommandLine(line, res);

        res.optimized = line.hasOption(OPT_OPTIMIZED);

        final String reposDirName;
        if (line.hasOption(OPT_REPOS_DIR_L)) {
            reposDirName = line.getOptionValue(OPT_REPOS_DIR_L);
        } else {
            reposDirName = Config.DEFAULT_REPOS_DIR_NAME;
        }

        File reposDir = new File(reposDirName);
        if (!reposDir.exists() || !reposDir.isDirectory()) {
            throw new RuntimeException(
                    "The repository directory does not exist or is not a directory: " + reposDir.getAbsolutePath());
        }
        res.reposDir = reposDir.getAbsolutePath();

        List<String> snapshotDateNames = line.getArgList();
        if (!snapshotDateNames.isEmpty()) {
            if (res.skunkMode == SkunkMode.CHECKOUT) {
                throw new RuntimeException("Processing individual snapshots is not supported in"
                        + " `--" + OPT_CHECKOUT_L + "' mode.");
            }
            ProjectInformationConfig.parseSnapshotFilterDates(snapshotDateNames, res);
        }

        return res;
    }

    private int parseCommitWindowSizeOrDie(CommandLine line) {
        String windowSizeString = line.getOptionValue(OPT_COMMIT_WINDOW_SIZE);
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

    private void parseSmellDetectionArgs(Config res, CommandLine line) {
        String smellShortName = line.getOptionValue(OPT_DETECT_L);
        try {
            res.smell = Smell.valueOf(smellShortName);
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

        final String smellConfigsDirName;
        if (line.hasOption(OPT_SMELL_CONFIGS_DIR_L)) {
            smellConfigsDirName = line.getOptionValue(OPT_SMELL_CONFIGS_DIR_L);
        } else {
            smellConfigsDirName = Config.DEFAULT_SMELL_CONFIGS_DIR_NAME;
        }

        File smellConfigsDir = new File(smellConfigsDirName);
        if (!smellConfigsDir.isDirectory()) {
            throw new RuntimeException("Smell configurations directory does not exist or is not a directory: "
                    + smellConfigsDir.getAbsolutePath());
        }

        File smellConfigFile = new File(smellConfigsDir, res.smell.configFileName);
        if (smellConfigFile.exists() && !smellConfigFile.isDirectory()) {
            res.smellConfig = smellConfigFile.getAbsolutePath();
        } else {
            throw new RuntimeException("The smell detection configuration file does not exist or is a directory: "
                    + smellConfigFile.getAbsolutePath());
        }
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
                                + "Skunk. [Default=" + Config.DEFAULT_SMELL_CONFIGS_DIR_NAME + "]")
                        .hasArg().argName("DIR").build());

        String validSmellArgs = getValidSmellArgs();

        options.addOption(Option.builder().longOpt(OPT_REPOS_DIR_L)
                .desc("Directory below which the repository of the project (specified via `--" + OPT_PROJECT_L
                        + "') can be found." + " [Default=" + Config.DEFAULT_REPOS_DIR_NAME + "]")
                .hasArg().argName("DIR").build());


        options.addOption(Option.builder(String.valueOf(OPT_COMMIT_WINDOW_SIZE)).longOpt(OPT_COMMIT_WINDOW_SIZE_L)
                .desc("Size of a commit window, specified as a positive integer. This option is only relevant during" +
                        " snapshot creation, i.e., when running in `--" + OPT_CHECKOUT_L
                        + "' mode." + " [Default=" + Config.DEFAULT_COMMIT_WINDOW_SIZE + "]")
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
                .argName(validSmellArgs)
                .build());

        options.addOptionGroup(skunkModeOptions);

        options.addOption(Option.builder(OPT_OPTIMIZED).longOpt("optimized")
                .desc("Magic optimization option. I don't know what it does.").build());
        // @formatter:on
        return options;
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

    private String progName() {
        return this.getClass().getSimpleName();
    }
}
