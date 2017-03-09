package de.ovgu.skunk.bugs.createsnapshots.main;

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
    public static final String REVISIONS_FILE_BASENAME = "revisionsFull.csv";
    private static Logger LOG = Logger.getLogger(CreateSnapshots.class);

    public static class Config {
        public static final int DEFAULT_COMMIT_WINDOW_SIZE = 100;
        private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
        private String reposDir = null; // "/home/hnes/Masterarbeit/Repositories/";
        public static final String DEFAULT_REPOS_DIR_NAME = "repos";
        private String smellConfig = null;
        public static final String DEFAULT_SMELL_CONFIGS_DIR_NAME = "smellconfigs";
        private String resultsDir = null;
        public static final String DEFAULT_RESULTS_DIR_NAME = "results";
        private String snapshotsDir = null;
        public static final String DEFAULT_SNAPSHOTS_DIR_NAME = "snapshots";
        private String project = null;
        public SkunkMode skunkMode = null;
        public Smell smell = null;
        public boolean optimized = false;

        public String smellModeFile() {
            return smell.fileName;
        }

        public File projectRepoDir() {
            return new File(reposDir, project);
        }

        public File projectResultsDir() {
            return new File(resultsDir, project);
        }

        public File projectSnapshotsDir() {
            return new File(snapshotsDir, project);
        }

        public int commitWindowSize() {
            return DEFAULT_COMMIT_WINDOW_SIZE;
        }

        public synchronized File tmpSnapshotDir(Date snapshotDate) {
            return new File(projectSnapshotsDir(), dateFormatter.format(snapshotDate));
        }

        public synchronized File resultsSnapshotDir(Date snapshotDate) {
            return new File(projectResultsDir(), dateFormatter.format(snapshotDate));
        }

        public File projectRevisionsCsvFile() {
            return new File(projectResultsDir(), REVISIONS_FILE_BASENAME);
        }

        public File projectInfoCsv() {
            return new File(projectResultsDir(), "projectInfo.csv");
        }

        public File projectAnalysisCsv() {
            return new File(projectResultsDir(), "projectAnalysis.csv");
        }
    }

    private Config conf;

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
        SOURCE {
            @Override
            public ISkunkModeStrategy getNewStrategyInstance(Config conf) {
                return new SourceStrategy(conf);
            }
        },

        PREPROCESS {
            @Override
            public ISkunkModeStrategy getNewStrategyInstance(Config conf) {
                return new PreprocessStrategy(conf);
            }
        },

        PROCESSED {
            @Override
            public ISkunkModeStrategy getNewStrategyInstance(Config conf) {
                return new ProcessedStrategy(conf);
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
            new CreateSnapshots().run(args);
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
         * @param previousSnapshot The previous snapshot
         * @param curSnapshot      Start date of the current snapshot
         */
        void ensureSnapshot(ISnapshot previousSnapshot, ProperSnapshot curSnapshot);

        /**
         * Run cppstats (if necessary) and Skunk
         *
         * @param previousSnapshot The previous snapshot
         * @param snapshot         The current snapshot
         */
        void processSnapshot(ISnapshot previousSnapshot, ProperSnapshot snapshot);
    }

    static class SourceStrategy implements ISkunkModeStrategy {
        private final Config conf;

        public SourceStrategy(Config conf) {
            this.conf = conf;
        }

        @Override
        public void removeOutputFiles() {
            File projInfoCsv = conf.projectInfoCsv();
            if (projInfoCsv.exists()) {
                LOG.warn("Running in SOURCE mode, but project info CSV already exists. It will be overwritten: "
                        + projInfoCsv.getAbsolutePath());
                projInfoCsv.delete();
            }
            File projAnalysisCsv = conf.projectAnalysisCsv();
            if (projAnalysisCsv.exists()) {
                LOG.warn("Running in SOURCE mode, but project analysis CSV already exists. It will be overwritten: "
                        + projAnalysisCsv.getAbsolutePath());
                projAnalysisCsv.delete();
            }
        }

        @Override
        public void ensureSnapshot(ISnapshot previousSnapShot, ProperSnapshot curSnapshot) {
            // GIT CHECKOUT
            gitCheckout(curSnapshot);
            // Anzahl der .c Dateien checken
            LOG.debug("Looking for checkout .c files in directory " + conf.projectRepoDir().getAbsolutePath());
            List<File> filesFound = FileFinder.find(conf.projectRepoDir(), "(.*\\.c$)");
            final int filesCount = filesFound.size();
            LOG.info(String.format("Found %d .c file%s in %s", filesCount, filesCount == 1 ? "" : "s",
                    conf.projectRepoDir().getAbsolutePath()));
            // In CSV Datei schreiben
            conf.projectResultsDir().mkdirs();
            writeProjectCsv(curSnapshot, filesFound);
            writeProjectAnalysisCsv(curSnapshot, filesFound);
            final File curSnapshotDir = conf.tmpSnapshotDir(curSnapshot.revisionDate());
            curSnapshotDir.mkdirs();
            copyCheckoutToTmpSnapshotDir(filesFound, previousSnapShot, curSnapshot);
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

        private void writeProjectAnalysisCsv(ProperSnapshot snapshot, List<File> filesFound) {
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

        private void writeProjectCsv(final ProperSnapshot snapshot, List<File> filesFound) {
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

        private void copyCheckoutToTmpSnapshotDir(List<File> filesInCurrentCheckout, ISnapshot previousSnapShot,
                                                  ProperSnapshot curSnapshot) {
            final File cppstatsDir = new File(conf.tmpSnapshotDir(curSnapshot.revisionDate()), "source");
            // Copy Files
            if (conf.optimized && (previousSnapShot instanceof ProperSnapshot)) {
                Collection<File> changedFiles = previousSnapShot.computeChangedFiles(filesInCurrentCheckout,
                        curSnapshot);
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
        public void processSnapshot(ISnapshot previousSnapshot, ProperSnapshot snapshot) {
            final Date snapshotDate = snapshot.revisionDate();
            final File resultsSnapshotDir = conf.resultsSnapshotDir(snapshotDate);
            final File tmpSnapshotDir = conf.tmpSnapshotDir(snapshotDate);
            resultsSnapshotDir.mkdirs();
            List<String> args = new ArrayList<>();
            args.add(conf.smellConfig /* ARG1 */);
            args.add(resultsSnapshotDir.getAbsolutePath() /* ARG2 */);
            final Date prevSnapshotDate = previousSnapshot.revisionDate();
            if (prevSnapshotDate != null) {
                final File prevSnapshotDir = conf.tmpSnapshotDir(prevSnapshotDate);
                final File prevCppstatsInputList = new File(prevSnapshotDir, CPPSTATS_INPUT_TXT);
                args.add("--prepareFrom=" + prevCppstatsInputList.getAbsolutePath());
            } else {
                args.add("--lazyPreparation");
            }
            runExternalCommand(CPP_SKUNK_PROG, tmpSnapshotDir /* WD */, args.toArray(new String[args.size()]));
            saveSnapshotCommitsHashes(snapshot);
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
        public void ensureSnapshot(ISnapshot previousSnapShot, ProperSnapshot curSnapshot) {
            // The snapshot has already been created in a previous run in SOURCE
            // mode --> Nothing to do.
        }

        @Override
        public void processSnapshot(ISnapshot previousSnapshot, ProperSnapshot snapshot) {
            Date snapshotDate = snapshot.revisionDate();
            File workingDir = conf.resultsSnapshotDir(snapshotDate);
            File snapshotDir = conf.tmpSnapshotDir(snapshotDate);
            runExternalCommand(SKUNK_PROG, workingDir, "--source=" + snapshotDir.getAbsolutePath(), "--save-intermediate");
        }
    }

    static class ProcessedStrategy implements ISkunkModeStrategy {
        private final Config conf;

        public ProcessedStrategy(Config conf) {
            this.conf = conf;
        }

        @Override
        public void removeOutputFiles() {
            // Nothing to do.
        }

        @Override
        public void ensureSnapshot(ISnapshot previousSnapShot, ProperSnapshot curSnapshot) {
            // The snapshot has already been created in a previous run in SOURCE
            // mode --> Nothing to do.
        }

        @Override
        public void processSnapshot(ISnapshot previousSnapshot, ProperSnapshot snapshot) {
            Date snapshotDate = snapshot.revisionDate();
            File resultsDir = conf.resultsSnapshotDir(snapshotDate);
            runExternalCommand(SKUNK_PROG, resultsDir, "--processed=.", "--config=" + conf.smellConfig);
        }
    }

    private void run(String[] args) {
        this.conf = this.parseCommandLineArgs(args);
        final ISkunkModeStrategy skunkStrategy = conf.skunkMode.getNewStrategyInstance(conf);

        revisionsReader = new RevisionsCsvReader(conf.projectRevisionsCsvFile(), conf.commitWindowSize());
        revisionsReader.processFile();
        skunkStrategy.removeOutputFiles();
        // Date prevDate = revisionsReader.bugHashes.firstKey();
        // for (Date curDate : revisionsReader.bugHashes.keySet()) {
        ISnapshot previousSnapshot = NullSnapshot.getInstance();
        for (ProperSnapshot curSnapshot : revisionsReader.getSnapshots()) {
            LOG.info("Processing snapshot " + curSnapshot);
            // LOG.debug("Key: " + curDateForm + " - " + "Value: " +
            // revisionsReader.bugHashes.get(curDate));
            skunkStrategy.ensureSnapshot(previousSnapshot, curSnapshot);
            skunkStrategy.processSnapshot(previousSnapshot, curSnapshot);
            // Ergebnis kopieren in eigenen Results Ordner
            if (conf.smell != null) {
                File sourcePath = conf.resultsSnapshotDir(curSnapshot.startDate());
                File smellResultsDir = new File(conf.projectResultsDir(), conf.smell.name() + "Res");
                smellResultsDir.mkdirs(); // Directories erstellen
                moveSnapshotSmellDetectionResults(curSnapshot, sourcePath, smellResultsDir);
            }
            previousSnapshot = curSnapshot;
        }
    }

    private void moveSnapshotSmellDetectionResults(ProperSnapshot snapshot, File sourcePath, File smellResultsDir) {
        final String snapshotDateString = snapshot.revisionDateString();
        List<File> filesFindCSV = FileFinder.find(sourcePath, "(.*\\.csv$)");
        // umbennen und verschieben von CSV Dateien (Smell Severity)
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
        // Rename and move XML files (Smell Location)
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
                    somethingWasAlive = true;
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
    private static final String OPT_SOURCE_L = "source";
    private static final String OPT_PREPROCESS_L = "preprocess";
    private static final String OPT_PROCESSED_L = "processed";
    /**
     * --smell=AB|AF|LF
     */
    private static final String OPT_SMELL = "s";
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
                formatter.printHelp(progName() + " [OPTIONS]",
                        "Create snapshots of a VCS repository and detect variability-aware smells in those snapshots using Skunk and cppstats.\n\t" +
                                "Snapshot creation requires information about the commits to this repository, which can be obtained by running " +
                                FindBugfixCommits.class.getSimpleName() + " on the repository.\n\t" +
                                "The snapshots will be created and an extensive Skunk analysis performed when this program is run with the `--" + OPT_SOURCE_L
                                + "' option. Subsequent runs with the `" +
                                OPT_PROCESSED_L + "' option will reuse the snapshots and Skunk analysis data and proceed much faster.\n\nOptions:\n",
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

        boolean needSmell = true;

        if (line.hasOption(OPT_SOURCE_L)) {
            res.skunkMode = SkunkMode.SOURCE;
        } else if (line.hasOption(OPT_PREPROCESS_L)) {
            res.skunkMode = SkunkMode.PREPROCESS;
            needSmell = false;
        } else if (line.hasOption(OPT_PROCESSED_L)) {
            res.skunkMode = SkunkMode.PROCESSED;
        } else {
            throw new RuntimeException(
                    "Either `--" + OPT_SOURCE_L + "', `--" + OPT_PREPROCESS_L + "' or `--" + OPT_PROCESSED_L + "' must be specified!");
        }

        if (needSmell) {
            String smellShortName = line.getOptionValue(OPT_SMELL);
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
                throw new RuntimeException("Illegal value for option -" + OPT_SMELL + ": " + smellShortName
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
        final String resultsDirName;
        if (line.hasOption(OPT_RESULTS_DIR_L)) {
            resultsDirName = line.getOptionValue(OPT_RESULTS_DIR_L);
        } else {
            resultsDirName = Config.DEFAULT_RESULTS_DIR_NAME;
        }
        File resultsDir = new File(resultsDirName);
        if (!resultsDir.exists() || !resultsDir.isDirectory()) {
            throw new RuntimeException(
                    "The results directory does not exist or is not a directory: " + resultsDir.getAbsolutePath());
        }
        res.resultsDir = resultsDir.getAbsolutePath();
        final String snapshotsDirName;
        if (line.hasOption(OPT_SNAPSHOTS_DIR_L)) {
            snapshotsDirName = line.getOptionValue(OPT_SNAPSHOTS_DIR_L);
        } else {
            snapshotsDirName = Config.DEFAULT_SNAPSHOTS_DIR_NAME;
        }
        File snapshotsDir = new File(snapshotsDirName);
        if (!snapshotsDir.exists() || !snapshotsDir.isDirectory()) {
            throw new RuntimeException(
                    "The snapshots directory does not exist or is not a directory: " + snapshotsDir.getAbsolutePath());
        }
        res.snapshotsDir = snapshotsDir.getAbsolutePath();
        res.project = line.getOptionValue(OPT_PROJECT);
        if (res.project == null || "".equals(res.project)) {
            throw new IllegalArgumentException(
                    "Project name (given via option `--" + OPT_PROJECT_L + "') must not be empty!");
        }
        return res;
    }

    private Options makeOptions(boolean forHelp) {
        boolean required = !forHelp;
        Options options = new Options();
        // @formatter:off
        // --help= option
        options.addOption(Option.builder(String.valueOf(OPT_HELP)).longOpt("help")
                .desc("print this help screen and exit").build());

        options.addOption(
                Option.builder().longOpt(OPT_SMELL_CONFIGS_DIR_L)
                        .desc("Name of the directory holding the smell detection configuration files for "
                                + "Skunk. [Default=" + Config.DEFAULT_SMELL_CONFIGS_DIR_NAME + "]")
                        .hasArg().argName("DIR").build());

        StringBuilder validSmellArgs = new StringBuilder();
        for (Smell m : Smell.values()) {
            if (validSmellArgs.length() > 0) {
                validSmellArgs.append("|");
            }
            validSmellArgs.append(m.name());
        }

        options.addOption(Option.builder(OPT_SMELL).longOpt("smell").desc("Name of smell for which to check." +
                " Required if running in modes `--" + OPT_SOURCE_L + "' or `--" + OPT_PROCESSED_L + "'").hasArg()
                .argName(validSmellArgs.toString()).build());

        options.addOption(Option.builder().longOpt(OPT_REPOS_DIR_L)
                .desc("Directory below which the repository of the project (specified via `--" + OPT_PROJECT_L
                        + "') can be found." + " [Default=" + Config.DEFAULT_REPOS_DIR_NAME + "]")
                .hasArg().argName("DIR").build());

        options.addOption(Option.builder(String.valueOf(OPT_PROJECT)).longOpt(OPT_PROJECT_L)
                .desc("Name of the project to be analyzed; must specify an existing git folder below the folder given via `--"
                        + OPT_REPOS_DIR_L + "'.")
                .hasArg().argName("DIR").required(required).build());

        options.addOption(Option.builder().longOpt(OPT_SNAPSHOTS_DIR_L).desc(
                "Snapshots will be created in this directory." + " [Default=" + Config.DEFAULT_SNAPSHOTS_DIR_NAME + "]")
                .hasArg().argName("DIR").build());

        options.addOption(Option.builder().longOpt(OPT_RESULTS_DIR_L)
                .desc("Directory where to put results." + " [Default=" + Config.DEFAULT_RESULTS_DIR_NAME + "]").hasArg()
                .argName("DIR").build());

        // --source, --preprocess and --processed options
        OptionGroup skunkModeOptions = new OptionGroup();
        skunkModeOptions.setRequired(required);

        skunkModeOptions.addOption(Option.builder().longOpt(OPT_SOURCE_L)
                .desc("Run Skunk on fresh set of sources, for which no analysis has been performed, yet.")
                .build());
        skunkModeOptions.addOption(Option.builder().longOpt(OPT_PREPROCESS_L)
                .desc("Recompute Skunk's on preprocessed on the cppstats files saved during a previous run of this"
                        + " tool with the `--" + OPT_SOURCE_L + "' option on.")
                .build());
        skunkModeOptions.addOption(Option.builder().longOpt(OPT_PROCESSED_L)
                .desc("Run Skunk on already preprocessed data saved during a previous run of this"
                        + " tool with the `--" + OPT_SOURCE_L + "' option on.")
                .build());

        options.addOptionGroup(skunkModeOptions);

        options.addOption(Option.builder(OPT_OPTIMIZED).longOpt("optimized")
                .desc("Magic optimization option. I don't know what it does.").build());
        // @formatter:on
        return options;
    }

    private String progName() {
        return this.getClass().getSimpleName();
    }
}
