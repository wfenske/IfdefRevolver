package de.ovgu.ifdefrevolver.bugs.correlate.input;

import com.opencsv.CSVReader;
import de.ovgu.ifdefrevolver.bugs.correlate.data.FileChangeHunk;
import de.ovgu.ifdefrevolver.bugs.correlate.data.IMinimalSnapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasProjectInfoFile;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasRevisionCsvFile;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasSnapshotsDir;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.input.RevisionsCsvReader;
import de.ovgu.ifdefrevolver.bugs.minecommits.OrderedRevisionsColumns;
import de.ovgu.ifdefrevolver.commitanalysis.IHasSnapshotFilter;
import de.ovgu.ifdefrevolver.util.SimpleCsvFileReader;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by wfenske on 09.02.17.
 */
public class ProjectInformationReader<TConfig extends IHasProjectInfoFile & IHasResultsDir & IHasSnapshotsDir & IHasRevisionCsvFile> {
    private static Logger LOG = Logger.getLogger(ProjectInformationReader.class);

    protected TConfig conf;

    protected SortedMap<Date, Snapshot> snapshots;

    protected SortedMap<IMinimalSnapshot, SortedMap<FileChangeHunk, String>> changedFilesBySnapshot = new TreeMap<>();
    protected SortedMap<IMinimalSnapshot, SortedMap<FileChangeHunk, String>> fixedFilesBySnapshot = new TreeMap<>();

    public ProjectInformationReader(TConfig conf) {
        this.conf = conf;
    }

    /**
     * <p>Main entry point of this class, reads the necessary project information:</p> <ul> <li>Snapshot information
     * (accessible via {@link #getSnapshots()})</li> <li>Revisions information (accessible via {@link
     * #getChangedFiles(IMinimalSnapshot)} and {@link #getFixedFiles(IMinimalSnapshot)})</li> </ul>
     */
    public void readSnapshotsAndRevisionsFile() {
        snapshots = readSnapshots();
        processRevisionsFile();
    }

    /**
     * Nimmt die ursprüngliche CSV-Datei von {@line FindBugfixCommits} und erstellt die
     * Listen der Bugfixes und geänderten Dateien mit ihren Änderungsdaten
     */
    private void processRevisionsFile() {
        LOG.info("Reading revisions file " + conf.revisionCsvFile());

        final String cvsSplitBy = ",";
        final DateFormat formatter = new SimpleDateFormat(OrderedRevisionsColumns.TIMESTAMP_FORMAT);

        final Map<String, Snapshot> snapshotsByCommit = mapSnapshotsToCommits();

        BufferedReader br = null;

        try {

            br = new BufferedReader(new FileReader(conf.revisionCsvFile()));
            String headerLine = br.readLine();
            RevisionsCsvReader.assertRevisionsFullCsvHeaderIsSane(headerLine);

            String line;
            while ((line = br.readLine()) != null) {

                // use comma as separator
                String[] modification = line.split(cvsSplitBy);
                String curHash = modification[0];

                Snapshot snapshot = snapshotsByCommit.get(curHash);

                if (snapshot == null) {
                    LOG.debug("Skipping commit " + curHash + ": not part of any snapshot");
                    continue;
                }

                boolean bugfixCommit = Boolean.parseBoolean(modification[1]);
                String strDate = modification[7];
                String fileName = modification[3];

                Date comDate;
                try {
                    comDate = formatter.parse(strDate);
                } catch (ParseException e) {
                    throw new RuntimeException("Could not parse date " + strDate + " in file "
                            + conf.revisionCsvFile(), e);
                }

                FileChangeHunk chFile = new FileChangeHunk(fileName, curHash, comDate);

                putChangedFile(this.changedFilesBySnapshot, snapshot, chFile, fileName);

                if (bugfixCommit) {
                    putChangedFile(this.fixedFilesBySnapshot, snapshot, chFile, fileName);
                    snapshot.addBugfixCommit(curHash);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Error reading revisions CSV file " + conf.revisionCsvFile(), e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    // Don't care
                }
            }
        }
    }

    protected SortedMap<Date, Snapshot> readSnapshots() {
        List<RawSnapshotInfo> rawSnapshotInfos = readRawSnapshotInfos();

        SortedMap<Date, Snapshot> result = new TreeMap<>();
        for (RawSnapshotInfo rawSnapshotInfo : rawSnapshotInfos) {
            Snapshot snapshot = new Snapshot(rawSnapshotInfo.sortIndex, rawSnapshotInfo.date,
                    rawSnapshotInfo.commitHashes, rawSnapshotInfo.snapshotDir);
            result.put(rawSnapshotInfo.date, snapshot);
        }

        return result;
    }

    public List<RawSnapshotInfo> readRawSnapshotInfos() {
        final DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        List<Date> snapshotDates = getProjectDates();
        File projSnapshotMetadataDir = new File(conf.projectResultsDir(), "snapshots");

        List<RawSnapshotInfo> rawSnapshotInfos = new ArrayList<>();
        for (Date snapshotDate : snapshotDates) {
            File snapshotFile = new File(projSnapshotMetadataDir,
                    formatter.format(snapshotDate) + ".csv");

            Pair<Integer, Set<String>> indexAndCommitHashes = readSnapshotCommitHashes(snapshotFile);
            File snapshotDir = conf.snapshotDirForDate(snapshotDate);

            RawSnapshotInfo snapshotInfo = new RawSnapshotInfo(indexAndCommitHashes.getKey(), snapshotDate, indexAndCommitHashes.getValue(), snapshotDir);
            rawSnapshotInfos.add(snapshotInfo);
        }
        return rawSnapshotInfos;
    }

    /**
     * @param snapshotFile
     * @return The sortindex and the commit hashes of the snapshot
     */
    private Pair<Integer, Set<String>> readSnapshotCommitHashes(final File snapshotFile) {
        Set<String> commitHashes = new LinkedHashSet<>();
        int snapshotIndex = -1;

        CSVReader reader = null;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(snapshotFile);
            reader = new CSVReader(fileReader);
            String[] header = reader.readNext();
            try {
                snapshotIndex = Integer.parseInt(header[0]);
                //snapshotDate = dateFormatter.parse(header[1]); // ignored
            } catch (NumberFormatException pe) {
                throw new RuntimeException(
                        "Error parsing header of snapshot file " + snapshotFile.getAbsolutePath());
            }
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                String commitHash = nextLine[0];
                commitHashes.add(commitHash);
            }
        } catch (IOException e1) {
            throw new RuntimeException(
                    "Error reading file " + snapshotFile.getAbsolutePath(), e1);
        } finally {
            CSVHelper.silentlyCloseReaders(reader, fileReader);
        }

        return Pair.of(snapshotIndex, commitHashes);
    }

    private List<Date> getProjectDates() {
        SimpleCsvFileReader<List<Date>> r = new SimpleCsvFileReader<List<Date>>() {
            List<Date> resultList = new ArrayList<>();
            final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

            @Override
            protected void processContentLine(String[] line) {
                String dateStr = line[1];
                Date verDate;
                try {
                    verDate = formatter.parse(dateStr);
                } catch (ParseException e) {
                    throw new RuntimeException("Error parsing date in  file "
                            + conf.projectInfoFile().getAbsolutePath(), e);
                }

                resultList.add(verDate);
            }

            @Override
            protected List<Date> finalizeResult() {
                return resultList;
            }
        };

        return r.readFile(conf.projectInfoFile());
    }

    /**
     * @return Changed files for the given snapshot
     */
    public SortedMap<FileChangeHunk, String> getChangedFiles(IMinimalSnapshot s) {
        return changedFilesBySnapshot.get(s);
    }

    /**
     * @return Fixed files for the given snapshot
     */
    public SortedMap<FileChangeHunk, String> getFixedFiles(IMinimalSnapshot s) {
        return fixedFilesBySnapshot.get(s);
    }

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
     * @param snapshotFilteringConfig
     * @return A fresh list of the dates of the requested snapshots
     */
    public Collection<Date> getSnapshotDatesFiltered(IHasSnapshotFilter snapshotFilteringConfig) {
        Collection<Snapshot> snapshotsToProcesses = getSnapshotsFiltered(snapshotFilteringConfig);
        Collection<Date> snapshotDates = new LinkedHashSet<>();
        for (Snapshot s : snapshotsToProcesses) {
            snapshotDates.add(s.getSnapshotDate());
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

    protected Map<String, Snapshot> mapSnapshotsToCommits() {
        Map<String, Snapshot> snapshotsByCommit = new HashMap<>();
        for (Snapshot s : snapshots.values()) {
            for (String commitHash : s.getCommitHashes()) {
                Snapshot previousSnapshot = snapshotsByCommit.put(commitHash, s);
                if (previousSnapshot != null) {
                    throw new RuntimeException("Commit " + commitHash + " occurs in two snapshots "
                            + previousSnapshot + " and " + s);
                }
            }
        }
        return snapshotsByCommit;
    }

    protected void putChangedFile(SortedMap<IMinimalSnapshot, SortedMap<FileChangeHunk, String>> map,
                                  IMinimalSnapshot snapshot, FileChangeHunk chFile, String fileName) {
        SortedMap<FileChangeHunk, String> changedFiles = ensureValueForKey(map, snapshot);
        changedFiles.put(chFile, fileName);
    }

    private static SortedMap<FileChangeHunk, String> ensureValueForKey(
            SortedMap<IMinimalSnapshot, SortedMap<FileChangeHunk, String>> filesBySnapshot,
            IMinimalSnapshot snapshot) {
        SortedMap<FileChangeHunk, String> value = filesBySnapshot.get(snapshot);
        if (value != null) {
            return value;
        }
        value = new TreeMap<>();
        filesBySnapshot.put(snapshot, value);
        return value;
    }
}
