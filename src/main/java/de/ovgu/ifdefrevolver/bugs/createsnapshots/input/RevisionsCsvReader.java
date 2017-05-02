package de.ovgu.ifdefrevolver.bugs.createsnapshots.input;

import de.ovgu.ifdefrevolver.bugs.correlate.input.ProjectInformationReader;
import de.ovgu.ifdefrevolver.bugs.correlate.input.RawSnapshotInfo;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.Commit;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.ProperSnapshot;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.main.CommitWindowSizeMode;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.main.CreateSnapshotsConfig;
import de.ovgu.ifdefrevolver.bugs.minecommits.OrderedRevisionsColumns;
import de.ovgu.ifdefrevolver.commitanalysis.IHasSnapshotFilter;
import de.ovgu.skunk.detection.output.CsvEnumUtils;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class RevisionsCsvReader {
    private static Logger LOG = Logger.getLogger(RevisionsCsvReader.class);

    private final File revisionsCsv;

    /**
     * Initialized in {@link #readAllCommits()}
     */
    private SortedSet<Commit> commits;
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
        this.commits = new TreeSet<>();
        this.commitsByHash = new HashMap<>();

        FileReader fr = null;
        BufferedReader br = null;
        final String cvsSplitBy = ",";
        final SimpleDateFormat formatter = new SimpleDateFormat(OrderedRevisionsColumns.TIMESTAMP_FORMAT);
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
                int branch = Integer.parseInt(hunkInfo[OrderedRevisionsColumns.BRANCH.ordinal()]);
                int positionInBranch = Integer.parseInt(hunkInfo[OrderedRevisionsColumns.POSITION.ordinal()]);
                String commitHash = hunkInfo[OrderedRevisionsColumns.COMMIT_ID.ordinal()];
                boolean bugfix = false;
                String comDateStr = hunkInfo[OrderedRevisionsColumns.TIMESTAMP.ordinal()];
                Date comDate = formatter.parse(comDateStr);

                Commit commit = new Commit(branch, positionInBranch, commitHash, comDate, bugfix);
                commitsByHash.put(commitHash, commit);
                commits.add(commit);
                if (bugfix) {
                    bugfixCount++;
                }

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
     * Checks the format of the header line of a revisionsFull.csv file.  The format is mandated by {@link
     * OrderedRevisionsColumns}.  The header fields must be comma-separated and at least all the fields of the enum must
     * occur in the header line.  They must occur in the same order.  Case and leading/trailing whitespace are ignored.
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
        String[] expectedHeader = CsvEnumUtils.headerRowStrings(OrderedRevisionsColumns.class);
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

        final SortedMap<Integer, SortedSet<Commit>> commitsByBranch = groupCommitsByBranch();
        final int totalNumberOfCommits = commitWindowSizeMode.countRelevantCommits(commits);

        int globalSnapshotIndex = 1;
        int totalNumSnapshots = 0;
        for (Map.Entry<Integer, SortedSet<Commit>> e : commitsByBranch.entrySet()) {
            SortedSet<Commit> commits = e.getValue();
            final int relevantCommits = commitWindowSizeMode.countRelevantCommits(commits);
            final int numSnapshots = relevantCommits / commitWindowSize;
            if (numSnapshots == 0) continue;
            totalNumSnapshots += numSnapshots;

            Iterator<Commit> iter = commits.iterator();
            // Skip the first couple of entries and advance to the first bug-fix
            // commit or the first regular commit, depending on size mode.
            Commit snapshotStart = commitWindowSizeMode.skipNRelevantCommits(iter, 0);

            for (int iSnapshotIndexInBranch = 1; iSnapshotIndexInBranch <= numSnapshots; iSnapshotIndexInBranch++) {
                Commit snapshotEnd = commitWindowSizeMode.skipNRelevantCommits(iter, commitWindowSize);
                if (snapshotEnd == null) {
                    SortedSet<Commit> snapshotCommits = commits.tailSet(snapshotStart);
                    snapshots.add(new ProperSnapshot(snapshotCommits, globalSnapshotIndex));
                    break;
                } else {
                    SortedSet<Commit> snapshotCommits = commits.subSet(snapshotStart, snapshotEnd);
                    snapshots.add(new ProperSnapshot(snapshotCommits, globalSnapshotIndex));
                    snapshotStart = snapshotEnd;
                }
                globalSnapshotIndex++;
            }
        }


        if (totalNumSnapshots == 0) {
            LOG.info("Insufficient amount of commits: " + totalNumberOfCommits + ". Need at least " + commitWindowSize
                    + ". No snapshots were created.");
            return;
        }

        validateSnapshots(conf);

        LOG.info("Successfully created " + totalNumSnapshots + " snapshots.");
    }

    private SortedMap<Integer, SortedSet<Commit>> groupCommitsByBranch() {
        SortedMap<Integer, SortedSet<Commit>> commitsByBranch = new TreeMap<>();
        for (Commit c : commits) {
            int branch = c.getBranch();
            SortedSet<Commit> commitsInBranch = commitsByBranch.get(branch);
            if (commitsInBranch == null) {
                commitsInBranch = new TreeSet<>();
                commitsByBranch.put(branch, commitsInBranch);
            }
            commitsInBranch.add(c);
        }
        return commitsByBranch;
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

        int expectedNumSnapshots = 0;
        SortedMap<Integer, SortedSet<Commit>> groupedCommits = groupCommitsByBranch();
        for (Collection<Commit> cs : groupedCommits.values()) {
            int snapshots = commitWindowSizeMode.countRelevantCommits(cs) / commitWindowSize;
            expectedNumSnapshots += snapshots;
        }
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

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");

        Map<String, ProperSnapshot> snapshotsByDate = new HashMap<>();
        for (ProperSnapshot s : allSnapshots) {
            String dateString = df.format(s.revisionDate());
            snapshotsByDate.put(dateString, s);
        }

        List<ProperSnapshot> result = new ArrayList<>();
        for (Date selectedDate : filterDates.get()) {
            String selectedDateString = df.format(selectedDate);
            ProperSnapshot s = snapshotsByDate.get(selectedDateString);
            if (s == null) {
                LOG.warn("No such snapshot: " + selectedDateString);
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
        SortedSet<Commit> fileChangesByCommitForSnapshot = new TreeSet<>();

        for (String commitHash : rawSnapshotInfo.commitHashes) {
            final Commit commit = commitsByHash.get(commitHash);
            if (commit == null) {
                throw new IllegalArgumentException("Snapshot " + rawSnapshotInfo + " refers to an unknown commit hash: " + commitHash);
            }
            fileChangesByCommitForSnapshot.add(commit);
        }

        ProperSnapshot result = new ProperSnapshot(fileChangesByCommitForSnapshot, rawSnapshotInfo.sortIndex);
        return result;
    }
}
