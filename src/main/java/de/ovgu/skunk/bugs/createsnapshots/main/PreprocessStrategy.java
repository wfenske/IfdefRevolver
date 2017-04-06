package de.ovgu.skunk.bugs.createsnapshots.main;

import de.ovgu.skunk.bugs.createsnapshots.data.ISnapshot;
import de.ovgu.skunk.bugs.createsnapshots.data.ProperSnapshot;
import de.ovgu.skunk.bugs.createsnapshots.input.RevisionsCsvReader;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Collection;
import java.util.Date;

/**
 * Only call Skunk to create its intermediate files, without doing anything else.  Requires cppstats to already have
 * run.
 */
class PreprocessStrategy implements ISnapshotProcessingModeStrategy {
    private static Logger LOG = Logger.getLogger(PreprocessStrategy.class);
    private RevisionsCsvReader revisionsCsvReader;

    private final CreateSnapshotsConfig conf;

    public PreprocessStrategy(CreateSnapshotsConfig conf) {
        this.conf = conf;
    }

    @Override
    public void readAllRevisionsAndComputeSnapshots() {
        this.revisionsCsvReader = new RevisionsCsvReader(conf.revisionCsvFile());
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
        File workingDir = conf.resultsSnapshotDir(snapshotDate);
        File snapshotDir = conf.tmpSnapshotDir(snapshotDate);
        if (!workingDir.isDirectory()) {
            boolean success = workingDir.mkdirs();
            if (!success) {
                throw new RuntimeException("Error creating directory or one of its parents: " + workingDir);
            }
        }
        CreateSnapshots.runExternalCommand(CreateSnapshotsConfig.SKUNK_PROG, workingDir, "--source=" + snapshotDir.getAbsolutePath(), "--save-intermediate");
    }

    @Override
    public String activityDisplayName() {
        return "Preprocessing cppstats sources with Skunk";
    }
}
