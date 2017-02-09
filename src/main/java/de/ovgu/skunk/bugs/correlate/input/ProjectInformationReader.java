package de.ovgu.skunk.bugs.correlate.input;

import de.ovgu.skunk.bugs.correlate.data.FileChangeHunk;
import de.ovgu.skunk.bugs.correlate.data.Snapshot;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created by wfenske on 09.02.17.
 */
public class ProjectInformationReader<TConfig> {
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
        String line = "";

        try {

            br = new BufferedReader(new FileReader(conf.revisionCsvFile()));
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
                // int bugfixCount = Integer.parseInt(commit[8]);
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
}
