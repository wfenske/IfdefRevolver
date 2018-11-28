package de.ovgu.ifdefrevolver.bugs.createsnapshots.main;

import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.ISnapshot;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.ProperSnapshot;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.Smell;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.input.FileFinder;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.input.RevisionsCsvReader;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Run smell detection for one smell definition on all the snapshots of a project
 */
class DetectSmellsStrategy implements ISnapshotProcessingModeStrategy {
    private static Logger LOG = Logger.getLogger(DetectSmellsStrategy.class);
    private RevisionsCsvReader revisionsCsvReader;

    private final CommitsDistanceDb commitsDb;
    private final CreateSnapshotsConfig conf;

    public DetectSmellsStrategy(CommitsDistanceDb commitsDb, CreateSnapshotsConfig conf) {
        this.commitsDb = commitsDb;
        this.conf = conf;
    }

    @Override
    public void readAllRevisionsAndComputeSnapshots() {
        this.revisionsCsvReader = new RevisionsCsvReader(commitsDb, conf.revisionCsvFile());
        this.revisionsCsvReader.readAllCommits();
        this.revisionsCsvReader.readPrecomputedSnapshots(conf);
    }

    @Override
    public Collection<ProperSnapshot> getSnapshotsToProcess() {
        return this.revisionsCsvReader.getSnapshotsFiltered(conf);
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
        File resultsDir = conf.snapshotResultsDirForDate(snapshotDate);
        CreateSnapshots.runExternalCommand(CreateSnapshotsConfig.SKUNK_PROG, resultsDir, "--processed=.", "--config=" + conf.smellConfig());
        moveSnapshotSmellDetectionResults(currentSnapshot);
    }

    @Override
    public String activityDisplayName() {
        return "Detecting smell " + conf.getSmell();
    }

    private void moveSnapshotSmellDetectionResults(ProperSnapshot curSnapshot) {
        File sourcePath = conf.snapshotResultsDirForDate(curSnapshot.startDate());
        File smellResultsDir = new File(conf.projectResultsDir(), conf.getSmell().name() + "Res");
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
                        conf.getSmell().name() + "Res" + ".csv");
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
        if (conf.getSmell() == Smell.LF) {
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

    @Override
    public boolean snapshotAlreadyProcessed(ProperSnapshot snapshot) {
        // Too much hassle. Just have everything recomputed.
        return false;
    }
}
