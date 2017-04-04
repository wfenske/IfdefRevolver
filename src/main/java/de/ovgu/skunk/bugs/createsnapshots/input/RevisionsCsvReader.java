package de.ovgu.skunk.bugs.createsnapshots.input;

import de.ovgu.skunk.bugs.createsnapshots.data.Commit;
import de.ovgu.skunk.bugs.createsnapshots.data.FileChange;
import de.ovgu.skunk.bugs.createsnapshots.data.ProperSnapshot;
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
    private final int commitWindowSizeX;

    // Liste für geänderte Dateien
    // private SortedMap<FileChange, String> changedFiles = new
    // TreeMap<FileChange, String>();
    // Liste für jeden x-ten Bugfix Commit
    // private SortedMap<Date, String> bugHashes = new TreeMap<Date, String>();

    private SortedMap<Commit, Set<FileChange>> fileChangesByCommit;
    private List<ProperSnapshot> snapshots;

    /**
     * Instantiates a new CSVReader
     *
     * @param revisionsCsv the path of the revisionsFull.csv
     */
    public RevisionsCsvReader(File revisionsCsv, int commitWindowSize) {
        this.revisionsCsv = revisionsCsv;
        this.commitWindowSizeX = commitWindowSize;
    }

    public void readAllRevisionsAndComputeSnapshots() {
        fileChangesByCommit = new TreeMap<>();

        readAllFileChanges();
        computeSnapshots();
    }

    private int readAllFileChanges() {
        LOG.info("Reading all file changes in " + this.revisionsCsv.getAbsolutePath());

        Map<String, Commit> commitsByHash = new HashMap<>();

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

    private void computeSnapshots() {
        LOG.info("Computing snapshots from " + revisionsCsv.getAbsolutePath());
        snapshots = new ArrayList<>();

        if (commitWindowSizeX <= 0) {
            throw new IllegalArgumentException("Invalid commit window size (should be >= 1): " + commitWindowSizeX);
        }

        final int totalNumberOfCommits = fileChangesByCommit.size();
        final int numSnapshots = totalNumberOfCommits / commitWindowSizeX;
        final int skipFront = totalNumberOfCommits % commitWindowSizeX;

        if (numSnapshots == 0) {
            LOG.info("Insufficient amount of commits: " + totalNumberOfCommits + ". Need at least " + commitWindowSizeX
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
        Commit snapshotStart = skipNCommits(iter, skipFront + 1);

        int sortIndex = 1;
        while (true) {
            Commit snapshotEnd = skipNCommits(iter, commitWindowSizeX);
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

        validateSnapshots();

        LOG.info("Successfully created " + numSnapshots + " snapshots.");
    }

    /**
     * Validate the snapshots in {@link #snapshots}. If something is wrong,
     * throw an {@link AssertionError}
     *
     * @throws AssertionError if the contents of {@link #snapshots} are invalid
     */
    private void validateSnapshots() {
        for (ProperSnapshot snapshot : snapshots) {
            snapshot.validate(commitWindowSizeX);
        }

        int expectedNumSnapshots = fileChangesByCommit.size() / commitWindowSizeX;
        int actualNumSnapshots = snapshots.size();
        if (actualNumSnapshots != expectedNumSnapshots) {
            throw new AssertionError("Expected " + expectedNumSnapshots + " snapshots to be created, but got  "
                    + actualNumSnapshots + ".");
        }
    }

    /**
     * Call {@link Iterator#next()} until n-many bug fix-commits have been seen,
     * or until the iterator does not provide any more elements.
     *
     * @param iter an Iterator
     * @param n    A non-negative number indicating the number of bug-fixes which
     *             should be skipped. If 0, the iterator will be advanced to the
     *             next bug-fix commit, if such a commit exists.
     * @return The n-th bug-fix commit. If no such bug-fix commit exists
     * (because the iterator stops returning elements before that),
     * <code>null</code> is returned.
     */
    private Commit skipNBugfixes(Iterator<Commit> iter, final int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Number of bug fixes to skip must not be < 0. Received " + n);
        }

        int fixesSeen = 0;
        while (iter.hasNext()) {
            Commit c = iter.next();
            if (c.isBugfix()) {
                fixesSeen++;
                if (fixesSeen >= n) {
                    return c;
                }
            }
        }

        return null;
    }

    /**
     * Call {@link Iterator#next()} until n-many commits have been seen,
     * or until the iterator does not provide any more elements.
     *
     * @param iter an Iterator
     * @param n    A non-negative number indicating the number of commits to
     *             skip. If 0, the iterator will be advanced to the next commit,
     *             if such a commit exists.
     * @return The n-th commit. If no such commit exists
     * (because the iterator stops returning elements before that),
     * <code>null</code> is returned.
     */
    private Commit skipNCommits(Iterator<Commit> iter, final int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Number of commits to skip must not be < 0. Received " + n);
        }

        int commitsSeen = 0;
        while (iter.hasNext()) {
            Commit c = iter.next();
            commitsSeen++;
            if (commitsSeen >= n) {
                return c;
            }
        }

        return null;
    }

    /**
     * The list of snapshots, created by calling {@link #readAllRevisionsAndComputeSnapshots()}. Each
     * snapshot contains exactly {@link #commitWindowSizeX} commits.
     *
     * @return The list of snapshots, in ascending order by date. Each snapshot
     * contains at least one commit, i.e., the maps are guaranteed to be
     * non-empty.
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
}
