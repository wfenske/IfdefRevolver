package de.ovgu.skunk.bugs.createsnapshots.input;

import de.ovgu.skunk.bugs.correlate.input.ProjectInformationReader;
import de.ovgu.skunk.bugs.correlate.input.RawSnapshotInfo;
import de.ovgu.skunk.bugs.createsnapshots.data.Commit;
import de.ovgu.skunk.bugs.createsnapshots.data.FileChange;
import de.ovgu.skunk.bugs.createsnapshots.data.ProperSnapshot;
import de.ovgu.skunk.bugs.createsnapshots.main.CommitWindowSizeMode;
import de.ovgu.skunk.bugs.createsnapshots.main.CreateSnapshotsConfig;
import de.ovgu.skunk.bugs.minecommits.RevisionsFullColumns;
import de.ovgu.skunk.commitanalysis.IHasSnapshotFilter;
import de.ovgu.skunk.detection.output.CsvEnumUtils;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class RevisionsCsvReader {
    private static Logger LOG = Logger.getLogger(RevisionsCsvReader.class);

    private final File revisionsCsv;

    /**
     * Initialized in {@link #readAllCommits()}
     */
    private SortedMap<Commit, Set<FileChange>> fileChangesByCommit;
    /**
     * Initialized in {@link #readAllCommits()}
     */
    private Map<String, Commit> commitsByHash;

    /**
     * Initialized by {@link #computeSnapshots(CreateSnapshotsConfig)} and {@link #readPrecomputedSnapshots(CreateSnapshotsConfig)}
     */
    private List<ProperSnapshot> snapshots;

    /**
     * Instantiates a new CSVReader
     *
     * @param revisionsCsv the path of the revisionsFull.csv
     */
    public RevisionsCsvReader(File revisionsCsv) {
        this.revisionsCsv = revisionsCsv;
    }

    public int readAllCommits() {
        LOG.info("Reading all file changes in " + this.revisionsCsv.getAbsolutePath());
        this.fileChangesByCommit = new TreeMap<>();
        this.commitsByHash = new HashMap<>();

        FileReader fr = null;
        BufferedReader br = null;
        final String cvsSplitBy = ",";
        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        int lineNo = 0;
        int bugfixCount = 0;

        try {
            fr = new FileReader(this.revisionsCsv);
            br = new BufferedReader(fr);

            String header = br.readLine();
            assertRevisionsFullCsvHeaderIsSane(header);

            String line;
            while ((line = br.readLine()) != null) {
                lineNo++;

                // use comma as separator
                String[] hunkInfo = line.split(cvsSplitBy);
                String commitHash = hunkInfo[0];
                boolean bugfix = Boolean.parseBoolean(hunkInfo[1]);
                // int bugfixCount = Integer.parseInt(hunkInfo[8]);
                String strDate = hunkInfo[7];
                Date comDate = formatter.parse(strDate);
                String fileName = hunkInfo[3];

                final Set<FileChange> filesChangedByCommit;
                Commit commit = commitsByHash.get(commitHash);
                if (commit == null) {
                    int sortIndex = commitsByHash.size();
                    commit = new Commit(sortIndex, commitHash, comDate, bugfix);
                    commitsByHash.put(commitHash, commit);
                    filesChangedByCommit = new HashSet<>();
                    fileChangesByCommit.put(commit, filesChangedByCommit);
                    if (bugfix) {
                        bugfixCount++;
                    }
                } else {
                    filesChangedByCommit = fileChangesByCommit.get(commit);
                }

                FileChange chFile = new FileChange(fileName, commit);
                filesChangedByCommit.add(chFile);

                if (lineNo % 10000 == 0) {
                    LOG.debug("Processed " + lineNo + " lines of file changes ...");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading file " + this.revisionsCsv.getAbsolutePath(), e);

        } catch (ParseException e) {
            throw new RuntimeException("Error reading contents of " + this.revisionsCsv.getAbsolutePath(), e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    // Don't care
                }
            } else {
                if (fr != null) {
                    try {
                        fr.close();
                    } catch (IOException e) {
                        // Don't care
                    }
                }
            }
        }

        LOG.debug("Processed " + lineNo + " lines in " + this.revisionsCsv.getAbsolutePath());
        LOG.info("Found " + commitsByHash.size() + " commits and " + bugfixCount + " bugfix(es) in "
                + this.revisionsCsv.getAbsolutePath());

        return lineNo;
    }

    /**
     * Checks the format of the header line of a revisionsFull.csv file.  The format is mandated by
     * {@link RevisionsFullColumns}.  The header fields must be comma-separated and at least all the fields of the enum
     * must occur in the header line.  They must occur in the same order.  Case and leading/trailing whitespace are
     * ignored.
     *
     * @param headerLine First line of the revisionsCsvFile being read.
     * @throws IllegalArgumentException if the header line does not match the expected format
     * @throws NullPointerException     if the header line is <code>null</code>
     */
    public static void assertRevisionsFullCsvHeaderIsSane(String headerLine) {
        if (headerLine == null) {
            throw new NullPointerException("Header line of revisions file is null.");
        }
        String[] headerFields = headerLine.split(",");
        String[] expectedHeader = CsvEnumUtils.headerRowStrings(RevisionsFullColumns.class);
        if (headerFields.length < expectedHeader.length) {
            throw new IllegalArgumentException("Missing fields in header of revisions file. Expected " +
                    Arrays.toString(expectedHeader) + ". Got: " + headerLine);
        }
        for (int i = 0; i < expectedHeader.length; i++) {
            String headerField = headerFields[i].trim();
            String expectedField = expectedHeader[i];
            if (!headerField.equalsIgnoreCase(expectedField)) {
                throw new IllegalArgumentException("Mismatch in fields in header of revisions file. Expected " +
                        Arrays.toString(expectedHeader) + ". Got: " + headerLine + ". Expected field at position "
                        + (i + 1) + " to be " + expectedField + ". Got: " + headerField);
            }
        }
    }

    public void computeSnapshots(CreateSnapshotsConfig conf) {
        LOG.info("Computing snapshots from " + revisionsCsv.getAbsolutePath());
        snapshots = new ArrayList<>();
        final CommitWindowSizeMode commitWindowSizeMode = conf.commitWindowSizeMode();
        final int commitWindowSize = conf.commitWindowSize();

        if (commitWindowSize <= 0) {
            throw new IllegalArgumentException("Invalid commit window size (should be >= 1): " + commitWindowSize);
        }

        final int totalNumberOfCommits = commitWindowSizeMode.countRelevantCommits(fileChangesByCommit.keySet());
        final int numSnapshots = totalNumberOfCommits / commitWindowSize;
        final int skipFront = totalNumberOfCommits % commitWindowSize;

        if (numSnapshots == 0) {
            LOG.info("Insufficient amount of commits: " + totalNumberOfCommits + ". Need at least " + commitWindowSize
                    + ". No snapshots will be created.");
            return;
        }

        LOG.debug("Skipping first " + skipFront + " commits since they don't fit into a commit window.");
        Iterator<Commit> iter = fileChangesByCommit.keySet().iterator();
        // Skip the first couple of entries and advance to the first bug-fix
        // commit. Yes, we actually need to add 1 to the skipFront number
        // because if called, for instance, with 5, the return value will be the
        // 5th bugfix commit. However, what we really want is to go to the 6th
        // bugfix commit. This also works if skipFront is 0: In this case, the
        // function will stop at the first bug-fix commit, which is exactly what
        // we want.
        Commit snapshotStart = commitWindowSizeMode.skipNRelevantCommits(iter, skipFront + 1);

        int sortIndex = 1;
        while (true) {
            Commit snapshotEnd = commitWindowSizeMode.skipNRelevantCommits(iter, commitWindowSize);
            if (snapshotEnd == null) {
                SortedMap<Commit, Set<FileChange>> snapshotCommits = fileChangesByCommit.tailMap(snapshotStart);
                snapshots.add(new ProperSnapshot(snapshotCommits, sortIndex));
                break;
            } else {
                SortedMap<Commit, Set<FileChange>> snapshotCommits = fileChangesByCommit.subMap(snapshotStart,
                        snapshotEnd);
                snapshots.add(new ProperSnapshot(snapshotCommits, sortIndex));
                snapshotStart = snapshotEnd;
            }
            sortIndex++;
        }

        validateSnapshots(conf);

        LOG.info("Successfully created " + numSnapshots + " snapshots.");
    }

    /**
     * Validate the snapshots in {@link #snapshots}. If something is wrong,
     * throw an {@link AssertionError}
     *
     * @throws AssertionError if the contents of {@link #snapshots} are invalid
     */
    private void validateSnapshots(CreateSnapshotsConfig conf) {
        final CommitWindowSizeMode commitWindowSizeMode = conf.commitWindowSizeMode();
        final int commitWindowSize = conf.commitWindowSize();

        for (ProperSnapshot snapshot : snapshots) {
            commitWindowSizeMode.validateSnapshotSize(snapshot, commitWindowSize);
        }

        int expectedNumSnapshots = commitWindowSizeMode.countRelevantCommits(fileChangesByCommit.keySet()) / commitWindowSize;
        int actualNumSnapshots = snapshots.size();
        if (actualNumSnapshots != expectedNumSnapshots) {
            throw new AssertionError("Expected " + expectedNumSnapshots + " snapshots to be created, but got  "
                    + actualNumSnapshots + ".");
        }
    }

    /**
     * The list of snapshots. Each snapshot contains exactly the same number of commits.
     *
     * @return The list of snapshots, in ascending order by date. Each snapshot contains at least one commit, i.e., the
     * maps are guaranteed to be non-empty.
     */
    public List<ProperSnapshot> getSnapshots() {
        return snapshots;
    }

    /**
     * @return Snapshots, ordered according to the filter (if present) or by date (if no filter was given)
     */
    public Collection<ProperSnapshot> getSnapshotsFiltered(IHasSnapshotFilter snapshotFilteringConfig) {
        Optional<List<Date>> filterDates = snapshotFilteringConfig.getSnapshotFilter();
        List<ProperSnapshot> allSnapshots = this.getSnapshots();
        if (!filterDates.isPresent()) {
            return allSnapshots;
        }

        Map<Date, ProperSnapshot> snapshotsByDate = new HashMap<>();
        for (ProperSnapshot s : allSnapshots) {
            snapshotsByDate.put(s.revisionDate(), s);
        }

        List<ProperSnapshot> result = new ArrayList<>();
        for (Date selectedDate : filterDates.get()) {
            ProperSnapshot s = snapshotsByDate.get(selectedDate);
            if (s == null) {
                LOG.warn("No such snapshot: " + selectedDate);
            } else {
                result.add(s);
            }
        }

        return result;
    }

    public void readPrecomputedSnapshots(CreateSnapshotsConfig conf) {
        LOG.debug("Reading precomputed snapshots dates from " + conf.projectResultsDir().getAbsolutePath());

        ProjectInformationReader helperReader = new ProjectInformationReader(conf);
        List<RawSnapshotInfo> rawSnapshotInfos = helperReader.readRawSnapshotInfos();

        this.snapshots = new ArrayList<>();
        for (RawSnapshotInfo rawSnapshotInfo : rawSnapshotInfos) {
            ProperSnapshot snapshot = properSnapshotFromRawSnapshotInfo(rawSnapshotInfo);
            snapshots.add(snapshot);
        }

        LOG.info("Successfully read " + snapshots.size() + " snapshots.");
    }

    private ProperSnapshot properSnapshotFromRawSnapshotInfo(RawSnapshotInfo rawSnapshotInfo) {
        SortedMap<Commit, Set<FileChange>> fileChangesByCommitForSnapshot = new TreeMap<>();

        for (String commitHash : rawSnapshotInfo.commitHashes) {
            final Commit commit = commitsByHash.get(commitHash);
            if (commit == null) {
                throw new IllegalArgumentException("Snapshot " + rawSnapshotInfo + " refers to an unknown commit hash: " + commitHash);
            }
            Set<FileChange> fileChanges = fileChangesByCommit.get(commit);
            if (fileChanges == null) {
                throw new IllegalStateException("Internal error: no file changes for commit " + commit);
            }
            fileChangesByCommitForSnapshot.put(commit, fileChanges);
        }

        ProperSnapshot result = new ProperSnapshot(fileChangesByCommitForSnapshot, rawSnapshotInfo.sortIndex);
        return result;
    }
}
