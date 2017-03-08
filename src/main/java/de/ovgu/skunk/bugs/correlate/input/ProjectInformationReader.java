package de.ovgu.skunk.bugs.correlate.input;

import com.opencsv.CSVReader;
import de.ovgu.skunk.bugs.correlate.data.FileChangeHunk;
import de.ovgu.skunk.bugs.correlate.data.Snapshot;
import de.ovgu.skunk.bugs.correlate.main.IHasProjectInfoFile;
import de.ovgu.skunk.bugs.correlate.main.IHasResultsDir;
import de.ovgu.skunk.bugs.correlate.main.IHasRevisionCsvFile;
import de.ovgu.skunk.bugs.correlate.main.IHasSnapshotsDir;
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
    private static Logger log = Logger.getLogger(ProjectInformationReader.class);

    protected TConfig conf;

    protected SortedMap<Date, Snapshot> snapshots;

    protected SortedMap<Snapshot, SortedMap<FileChangeHunk, String>> changedFilesBySnapshot = new TreeMap<>();
    protected SortedMap<Snapshot, SortedMap<FileChangeHunk, String>> fixedFilesBySnapshot = new TreeMap<>();

    public ProjectInformationReader(TConfig conf) {
        this.conf = conf;
    }

    /**
     * <p>Main entry point of this class, reads the necessary project
     * information:</p>
     * <ul>
     * <li>Snapshot information (accessible via {@link #getSnapshots()})</li>
     * <li>Revisions information (accessible via {@link #getChangedFiles(Snapshot)} and {@link #getFixedFiles(Snapshot)})</li>
     * </ul>
     */
    public void readSnapshotsAndRevisionsFile() {
        snapshots = readSnapshots();
        processRevisionsFile();
    }

    /**
     * Nimmt die ursprüngliche CSV-Datei von MetricMiner2 und erstellt die
     * Listen der Bugfixes und geänderten Dateien mit ihren Änderungsdaten
     */
    private void processRevisionsFile() {
        log.info("Reading revisions file " + conf.revisionCsvFile());

        final String cvsSplitBy = ",";
        final DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        final Map<String, Snapshot> snapshotsByCommit = mapSnapshotsToCommits();

        BufferedReader br = null;

        try {

            br = new BufferedReader(new FileReader(conf.revisionCsvFile()));
            String line;
            while ((line = br.readLine()) != null) {

                // use comma as separator
                String[] modification = line.split(cvsSplitBy);
                String curHash = modification[0];

                Snapshot snapshot = snapshotsByCommit.get(curHash);

                if (snapshot == null) {
                    log.debug("Skipping commit " + curHash + ": not part of any snapshot");
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
        SortedMap<Date, Snapshot> result = new TreeMap<>();
        List<Date> snapshotDates = getProjectDates();

        final DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        File projSnapshotMetadataDir = new File(conf.projectResultsDir(), "snapshots");
        for (Date snapshotDate : snapshotDates) {
            File snapshotFile = new File(projSnapshotMetadataDir,
                    formatter.format(snapshotDate) + ".csv");
            Snapshot snapshot = readSnapshot(snapshotFile);
            result.put(snapshotDate, snapshot);
        }

        return result;
    }

    private Snapshot readSnapshot(File snapshotFile) {
        Set<String> commitHashes = new LinkedHashSet<>();
        int snapshotIndex = -1;
        Date snapshotDate = null;
        final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

        CSVReader reader = null;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(snapshotFile);
            reader = new CSVReader(fileReader);
            String[] header = reader.readNext();
            try {
                snapshotIndex = Integer.parseInt(header[0]);
                snapshotDate = dateFormatter.parse(header[1]);
            } catch (ParseException pe) {
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
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // We don't care if closing the reader fails.
                }
            } else if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    // We don't care if closing the reader fails.
                }
            }
        }

        File snapshotDir = conf.projectSnapshotDirForDate(snapshotDate);
        return new Snapshot(snapshotIndex, snapshotDate, commitHashes, snapshotDir);
    }

    private List<Date> getProjectDates() {
        List<Date> resultList = new ArrayList<>();
        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        CSVReader reader = null;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(conf.projectInfoFile());
            reader = new CSVReader(fileReader);
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                String dateStr = nextLine[1];
                Date verDate = null;
                try {
                    verDate = formatter.parse(dateStr);
                } catch (ParseException e) {
                    throw new RuntimeException("Error parsing date in  file "
                            + conf.projectInfoFile().getAbsolutePath(), e);
                }

                resultList.add(verDate);
            }
        } catch (IOException e1) {
            throw new RuntimeException(
                    "Error reading file " + conf.projectInfoFile().getAbsolutePath(), e1);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // We don't care if closing the reader fails.
                }
            } else if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    // We don't care if closing the reader fails.
                }
            }
        }

        return resultList;
    }

    /**
     * @return Changed files for the given snapshot
     */
    public SortedMap<FileChangeHunk, String> getChangedFiles(Snapshot s) {
        return changedFilesBySnapshot.get(s);
    }

    /**
     * @return Fixed files for the given snapshot
     */
    public SortedMap<FileChangeHunk, String> getFixedFiles(Snapshot s) {
        return fixedFilesBySnapshot.get(s);
    }

    /**
     * @return Snapshots, ordered by date
     */
    public SortedMap<Date, Snapshot> getSnapshots() {
        return snapshots;
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

    protected void putChangedFile(SortedMap<Snapshot, SortedMap<FileChangeHunk, String>> map,
                                  Snapshot snapshot, FileChangeHunk chFile, String fileName) {
        SortedMap<FileChangeHunk, String> changedFiles = ensureValueForKey(map, snapshot);
        changedFiles.put(chFile, fileName);
    }

    private static SortedMap<FileChangeHunk, String> ensureValueForKey(
            SortedMap<Snapshot, SortedMap<FileChangeHunk, String>> filesBySnapshot,
            Snapshot snapshot) {
        SortedMap<FileChangeHunk, String> value = filesBySnapshot.get(snapshot);
        if (value != null) {
            return value;
        }
        value = new TreeMap<>();
        filesBySnapshot.put(snapshot, value);
        return value;
    }
}
