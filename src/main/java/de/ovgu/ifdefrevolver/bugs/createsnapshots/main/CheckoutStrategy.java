package de.ovgu.ifdefrevolver.bugs.createsnapshots.main;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.ISnapshot;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.input.FileFinder;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.input.RevisionsCsvReader;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.util.FileUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * <p>Checks out a project from GIT and creates the commit windows in the project's <code>snapshots</code>
 * directory<p/> <ol> <li>Reads all the commits of a repo,</li> <li>computes commit windows and writes them to disc (to
 * the following files <code>projectInfo.csv</code>, <code>snapshots/YYYY-MM-DD.csv</code>, where YYYY-MM-DD is the date
 * of the first commit in the snapshot; both files reside in the project's results directory)</li>
 * <li>checks out the sources from the GIT repo</li> <li>runs cppstats on each of the checked out commit
 * windows.</li> </ol>
 */
class CheckoutStrategy implements ISnapshotProcessingModeStrategy {
    private static final String CPPSTATS_INPUT_TXT = "cppstats_input.txt";
    private static Logger LOG = Logger.getLogger(CheckoutStrategy.class);

    private final CommitsDistanceDb commitsDb;
    private final CreateSnapshotsConfig conf;
    private ISnapshot previousSnapshot;
    private RevisionsCsvReader revisionsCsvReader;

    public CheckoutStrategy(CommitsDistanceDb commitsDb, CreateSnapshotsConfig conf) {
        this.commitsDb = commitsDb;
        this.conf = conf;
    }

    @Override
    public void readAllRevisionsAndComputeSnapshots() {
        this.revisionsCsvReader = new RevisionsCsvReader(commitsDb, conf.revisionCsvFile());
        this.revisionsCsvReader.readCommitsThatModifyCFiles();
        this.revisionsCsvReader.computeAndPersistSnapshots(conf);
    }

    @Override
    public Collection<Snapshot> getSnapshotsToProcess() {
        if (conf.getSnapshotFilter().isPresent()) {
            LOG.warn("Ignoring snapshot filter: cannot be used in conjunction with " + this.getClass().getSimpleName());
        }
        return this.revisionsCsvReader.getSnapshots();
    }

    @Override
    public boolean snapshotAlreadyProcessed(Snapshot snapshot) {
        File cppstatsConfigFile = cppstatsConfigFile(snapshot);
        File snapshotDir = snapshotDir(snapshot);
        File cppstatsGeneralCsv = new File(snapshotDir, "cppstats.csv");
        File cppstatsFeatureLocationsCsv = new File(snapshotDir, "cppstats_featurelocations.csv");
        return (FileUtils.isNonEmptyRegularFile(cppstatsConfigFile) &&
                FileUtils.isNonEmptyRegularFile(cppstatsGeneralCsv) &&
                FileUtils.isNonEmptyRegularFile(cppstatsFeatureLocationsCsv));
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
    public void ensureSnapshot(Snapshot currentSnapshot) {
        // GIT CHECKOUT
        gitCheckout(currentSnapshot);
        // Anzahl der .c Dateien checken
        File projectRepoDir = new File(conf.getRepoDir());
        LOG.debug("Looking for checkout .c files in directory " + projectRepoDir.getAbsolutePath());
        List<File> filesFound = FileFinder.find(projectRepoDir, "(.*\\.c$)");
        final int filesCount = filesFound.size();
        LOG.info(String.format("Found %d .c file%s in %s", filesCount, filesCount == 1 ? "" : "s",
                projectRepoDir.getAbsolutePath()));
        conf.projectResultsDir().mkdirs();
//        appendToProjectCsv(currentSnapshot, filesFound);
//        appendToProjectAnalysisCsv(currentSnapshot, filesFound);
        final File currentSnapshotDir = snapshotDir(currentSnapshot);
        currentSnapshotDir.mkdirs();
        copyCheckoutToTmpSnapshotDir(filesFound, currentSnapshot);
        writeCppstatsConfigFile(currentSnapshot);
    }

    /**
     * Git Checkout Script Aufruf
     *
     * @param snapshot
     */
    private void gitCheckout(Snapshot snapshot) {
        String hash = snapshot.getStartCommit().commitHash;
        File projectRepoDir = new File(conf.getRepoDir());
        CreateSnapshots.runExternalCommand(CreateSnapshotsConfig.GIT_PROG, projectRepoDir.getAbsoluteFile(), "checkout", "--force", hash);
    }

//    private void appendToProjectAnalysisCsv(ProperSnapshot snapshot, List<File> filesFound) {
//        final File csvOutFile = conf.projectAnalysisCsv();
//        final File projectRepoDir = new File(conf.getRepoDir());
//        FileWriter fileWriter = null;
//        BufferedWriter buff = null;
//        try {
//            fileWriter = new FileWriter(csvOutFile, true);
//            buff = new BufferedWriter(fileWriter);
//            for (File f : filesFound) {
//                String relativeFileName = CreateSnapshots.pathRelativeTo(f, projectRepoDir);
//                buff.write(relativeFileName + "," + snapshot.getStartDateString());
//                buff.newLine();
//            }
//        } catch (IOException e1) {
//            throw new RuntimeException("Error writing stuff to " + csvOutFile.getAbsolutePath(), e1);
//        } finally {
//            closeBufferedWriter(buff, fileWriter);
//        }
//    }

//    private void appendToProjectCsv(final Snapshot snapshot, List<File> filesFound) {
//        BufferedWriter buff = null;
//        FileWriter fileWriter = null;
//        try {
//            fileWriter = new FileWriter(conf.projectInfoCsv(), true);
//            buff = new BufferedWriter(fileWriter);
//            buff.write(snapshot.getStartCommit() + "," + snapshot.getStartDateString() + "," + filesFound.size());
//            buff.newLine();
//            LOG.debug("Added snapshot " + snapshot.getStartDateString() + " to "
//                    + conf.projectInfoCsv().getAbsolutePath());
//        } catch (IOException e1) {
//            throw new RuntimeException("Error appending snapshot " + snapshot.getStartDateString() + " to "
//                    + conf.projectInfoCsv().getAbsolutePath(), e1);
//        } finally {
//            closeBufferedWriter(buff, fileWriter);
//        }
//    }

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

    private void copyCheckoutToTmpSnapshotDir(List<File> filesInCurrentCheckout, Snapshot currentSnapshot) {
        final File cppstatsDir = new File(snapshotDir(currentSnapshot), "source");
        copyAllFiles(filesInCurrentCheckout, cppstatsDir);
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
        final File projectRepoDir = new File(conf.getRepoDir());
        String relFileName = CreateSnapshots.pathRelativeTo(file, projectRepoDir);
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
     * @param snapshot Snapshot for which to create the cppstats config file
     */
    private void writeCppstatsConfigFile(Snapshot snapshot) {
        final File snapshotDir = snapshotDir(snapshot);
        final File cppstatsConfigFile = cppstatsConfigFile(snapshot);
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(cppstatsConfigFile);
            writer.write(snapshotDir.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Error creating " + CPPSTATS_INPUT_TXT + " in " + snapshotDir, e);
        } finally {
            if (writer != null) writer.close();
        }
        LOG.info("Wrote " + cppstatsConfigFile.getAbsolutePath());
    }

    private File cppstatsConfigFile(Snapshot snapshot) {
        File snapshotDir = snapshotDir(snapshot);
        return new File(snapshotDir, CPPSTATS_INPUT_TXT);
    }

    private File snapshotDir(Snapshot snapshot) {
        return conf.snapshotDirForDate(snapshot.getStartDate());
    }

    @Override
    public void processSnapshot(Snapshot currentSnapshot) {
        final Date snapshotDate = currentSnapshot.getStartDate();
        final File resultsSnapshotDir = conf.snapshotResultsDirForDate(snapshotDate);
        final File tmpSnapshotDir = conf.snapshotDirForDate(snapshotDate);
        resultsSnapshotDir.mkdirs();
        List<String> args = new ArrayList<>();
        //args.add(conf.smellConfig /* ARG1 */);
        //args.add(snapshotResultsDirForDate.getAbsolutePath() /* ARG2 */);
        final Date prevSnapshotDate = previousSnapshot.getStartDate();
        if (prevSnapshotDate != null) {
            final File prevSnapshotDir = conf.snapshotDirForDate(prevSnapshotDate);
            final File prevCppstatsInputList = new File(prevSnapshotDir, CPPSTATS_INPUT_TXT);
            args.add("--prepareFrom=" + prevCppstatsInputList.getAbsolutePath());
        } else {
            args.add("--lazyPreparation");
        }
        CreateSnapshots.runExternalCommand(CreateSnapshotsConfig.CPP_SKUNK_PROG, tmpSnapshotDir /* WD */, args.toArray(new String[args.size()]));
    }

    @Override
    public String activityDisplayName() {
        return "Checking out sources and running cppstats";
    }
}
