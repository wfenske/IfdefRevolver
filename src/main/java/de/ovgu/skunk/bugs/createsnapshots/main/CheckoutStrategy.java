package de.ovgu.skunk.bugs.createsnapshots.main;

import de.ovgu.skunk.bugs.createsnapshots.data.Commit;
import de.ovgu.skunk.bugs.createsnapshots.data.ISnapshot;
import de.ovgu.skunk.bugs.createsnapshots.data.ProperSnapshot;
import de.ovgu.skunk.bugs.createsnapshots.input.FileFinder;
import de.ovgu.skunk.bugs.createsnapshots.input.RevisionsCsvReader;
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
 * directory<p/> <ol> <li>Reads all the commits of a repo,</li> <li>computes commit windows and writes them to disc
 * (to the following files <code>projectInfo.csv</code>, <code>snapshots/YYYY-MM-DD.csv</code>, where YYYY-MM-DD is
 * the date of the first commit in the snapshot; both files reside in the project's results directory)</li>
 * <li>checks out the sources from the GIT repo</li> <li>runs cppstats on each of the checked out commit
 * windows.</li> </ol>
 */
class CheckoutStrategy implements ISnapshotProcessingModeStrategy {
    private static final String CPPSTATS_INPUT_TXT = "cppstats_input.txt";
    private static Logger LOG = Logger.getLogger(CheckoutStrategy.class);

    private final CreateSnapshotsConfig conf;
    private ISnapshot previousSnapshot;
    private RevisionsCsvReader revisionsCsvReader;

    public CheckoutStrategy(CreateSnapshotsConfig conf) {
        this.conf = conf;
    }

    @Override
    public void readAllRevisionsAndComputeSnapshots() {
        this.revisionsCsvReader = new RevisionsCsvReader(conf.revisionCsvFile());
        this.revisionsCsvReader.readAllCommits();
        this.revisionsCsvReader.computeSnapshots(conf);
    }

    @Override
    public Collection<ProperSnapshot> getSnapshotsToProcess() {
        if (conf.getSnapshotFilter().isPresent()) {
            LOG.warn("Ignoring snapshot filter: cannot be used in conjunction with " + this.getClass().getSimpleName());
        }
        return this.revisionsCsvReader.getSnapshots();
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
        CreateSnapshots.runExternalCommand(CreateSnapshotsConfig.GIT_PROG, conf.projectRepoDir().getAbsoluteFile(), "checkout", "--force", hash);
    }

    private void appendToProjectAnalysisCsv(ProperSnapshot snapshot, List<File> filesFound) {
        final File csvOutFile = conf.projectAnalysisCsv();
        FileWriter fileWriter = null;
        BufferedWriter buff = null;
        try {
            fileWriter = new FileWriter(csvOutFile, true);
            buff = new BufferedWriter(fileWriter);
            for (File f : filesFound) {
                String relativeFileName = CreateSnapshots.pathRelativeTo(f, conf.projectRepoDir());
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
        if (conf.isOptimized()) {
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
        String relFileName = CreateSnapshots.pathRelativeTo(file, conf.projectRepoDir());
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
        CreateSnapshots.runExternalCommand(CreateSnapshotsConfig.CPP_SKUNK_PROG, tmpSnapshotDir /* WD */, args.toArray(new String[args.size()]));
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
