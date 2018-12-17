package de.ovgu.ifdefrevolver.bugs.correlate.input;

import de.ovgu.ifdefrevolver.bugs.correlate.data.IMinimalSnapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasProjectInfoFile;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasRevisionCsvFile;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasSnapshotsDir;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.input.RevisionsCsvReader;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.commitanalysis.IHasSnapshotFilter;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * Created by wfenske on 09.02.17.
 */
public class ProjectInformationReader<TConfig extends IHasProjectInfoFile & IHasResultsDir & IHasSnapshotsDir & IHasRevisionCsvFile> {
    private static Logger LOG = Logger.getLogger(ProjectInformationReader.class);

    protected final TConfig conf;
    protected final CommitsDistanceDb commitsDb;


    protected SortedMap<Date, Snapshot> snapshots;

    private RevisionsCsvReader revisionsReader;

    //protected SortedMap<IMinimalSnapshot, SortedMap<FileChangeHunk, String>> changedFilesBySnapshot = new TreeMap<>();
    //protected SortedMap<IMinimalSnapshot, SortedMap<FileChangeHunk, String>> fixedFilesBySnapshot = new TreeMap<>();

    public ProjectInformationReader(TConfig conf, CommitsDistanceDb commitsDb) {
        this.conf = conf;
        this.commitsDb = commitsDb;
    }

    public CommitsDistanceDb commitsDb() {
        return this.commitsDb;
    }

    /**
     * <p>Main entry point of this class, reads the necessary project information:</p> <ul> <li>Snapshot information
     * (accessible via {@link #getSnapshots()})</li> <li>Revisions information (accessible via {@link
     * #getChangedFiles(IMinimalSnapshot)} and {@link #getFixedFiles(IMinimalSnapshot)})</li> </ul>
     */
    public void readSnapshotsAndRevisionsFile() {
        LOG.info("Reading revisions file " + conf.revisionCsvFile());
        revisionsReader = new RevisionsCsvReader(commitsDb, conf.revisionCsvFile());
        revisionsReader.readCommitsThatModifyCFiles();
        snapshots = readSnapshots();
    }

    protected SortedMap<Date, Snapshot> readSnapshots() {
        revisionsReader.readPrecomputedSnapshots(conf);
        final List<Snapshot> snapshots = revisionsReader.getSnapshots();
        SortedMap<Date, Snapshot> result = new TreeMap<>();
        for (Snapshot s : snapshots) {
            result.put(s.getStartDate(), s);
        }

        return result;
    }

//    /**
//     * @return Changed files for the given snapshot
//     */
//    public SortedMap<FileChangeHunk, String> getChangedFiles(IMinimalSnapshot s) {
//        return changedFilesBySnapshot.get(s);
//    }

//    /**
//     * @return Fixed files for the given snapshot
//     */
//    public SortedMap<FileChangeHunk, String> getFixedFiles(IMinimalSnapshot s) {
//        return fixedFilesBySnapshot.get(s);
//    }

    /**
     * @return Snapshots, ordered by date
     */
    public SortedMap<Date, Snapshot> getSnapshots() {
        return snapshots;
    }

    /**
     * @return Snapshots, ordered according to the filter (if present) or by date (if no filter was given)
     */
    public Collection<Snapshot> getSnapshotsFiltered(IHasSnapshotFilter snapshotFilteringConfig) {
        return getSnapshotsFiltered(this.getSnapshots(), snapshotFilteringConfig);
    }

    /**
     * @return Snapshots, ordered by date
     */
    public List<Snapshot> getAllSnapshots() {
        final List<Snapshot> result = new ArrayList<>(this.getSnapshots().values());
        Collections.sort(result);
        return result;
    }

    /**
     * @param snapshotFilteringConfig
     * @return A fresh list of the dates of the requested snapshots
     */
    public Collection<Date> getSnapshotDatesFiltered(IHasSnapshotFilter snapshotFilteringConfig) {
        Collection<Snapshot> snapshotsToProcesses = getSnapshotsFiltered(snapshotFilteringConfig);
        Collection<Date> snapshotDates = new LinkedHashSet<>();
        for (Snapshot s : snapshotsToProcesses) {
            snapshotDates.add(s.getStartDate());
        }
        return snapshotDates;
    }

    private static Collection<Snapshot> getSnapshotsFiltered(SortedMap<Date, Snapshot> allSnapshots, IHasSnapshotFilter snapshotFilteringConfig) {
        Optional<List<Date>> explicitSnapshotDates = snapshotFilteringConfig.getSnapshotFilter();
        final Collection<Snapshot> snapshotsToProcess;
        if (!explicitSnapshotDates.isPresent()) {
            return allSnapshots.values();
        }

        Collection<Date> explicitSnapshotDatesValue = explicitSnapshotDates.get();
        // by using a LinkedHashSet, we make sure that each date occurs only once.
        snapshotsToProcess = new LinkedHashSet<>(explicitSnapshotDatesValue.size());
        for (Date snapshotDate : explicitSnapshotDatesValue) {
            Snapshot snapshot = allSnapshots.get(snapshotDate);
            if (snapshot == null) {
                LOG.warn("No such snapshot: " + snapshotDate);
            } else {
                snapshotsToProcess.add(snapshot);
            }
        }

        return snapshotsToProcess;
    }

//    protected Map<Commit, Snapshot> mapSnapshotsToCommits() {
//        Map<Commit, Snapshot> snapshotsByCommit = new HashMap<>();
//        for (Snapshot s : snapshots.values()) {
//            for (Commit commit : s.getCommits()) {
//                Snapshot previousSnapshot = snapshotsByCommit.put(commit, s);
//                if (previousSnapshot != null) {
//                    throw new RuntimeException("Commit " + commit + " occurs in two snapshots "
//                            + previousSnapshot + " and " + s);
//                }
//            }
//        }
//        return snapshotsByCommit;
//    }
//
//    protected void putChangedFile(SortedMap<IMinimalSnapshot, SortedMap<FileChangeHunk, String>> map,
//                                  IMinimalSnapshot snapshot, FileChangeHunk chFile, String fileName) {
//        SortedMap<FileChangeHunk, String> changedFiles = ensureValueForKey(map, snapshot);
//        changedFiles.put(chFile, fileName);
//    }

//    private static SortedMap<FileChangeHunk, String> ensureValueForKey(
//            SortedMap<IMinimalSnapshot, SortedMap<FileChangeHunk, String>> filesBySnapshot,
//            IMinimalSnapshot snapshot) {
//        SortedMap<FileChangeHunk, String> value = filesBySnapshot.get(snapshot);
//        if (value != null) {
//            return value;
//        }
//        value = new TreeMap<>();
//        filesBySnapshot.put(snapshot, value);
//        return value;
//    }
}
