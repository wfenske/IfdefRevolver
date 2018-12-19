package de.ovgu.ifdefrevolver.bugs.createsnapshots.input;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasSnapshotsDir;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.main.CreateSnapshotsConfig;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.bugs.minecommits.OrderedRevisionsColumns;
import de.ovgu.ifdefrevolver.commitanalysis.IHasSnapshotFilter;
import de.ovgu.ifdefrevolver.commitanalysis.branchtraversal.SnapshotCreatingCommitWalker;
import de.ovgu.ifdefrevolver.commitanalysis.branchtraversal.SnapshotReader;
import de.ovgu.ifdefrevolver.commitanalysis.branchtraversal.WriteSnapshotsToCsvFilesStrategy;
import de.ovgu.skunk.detection.output.CsvEnumUtils;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

public class RevisionsCsvReader {
    private static Logger LOG = Logger.getLogger(RevisionsCsvReader.class);

    private final CommitsDistanceDb commitsDb;

    private final File revisionsCsv;

    /**
     * Initialized in {@link #readCommitsThatModifyCFiles()}
     */
    private Set<Commit> commitsThatModifyCFiles;

    /**
     * Initialized by {@link #computeAndPersistSnapshots(CreateSnapshotsConfig)} and {@link
     * #readPrecomputedSnapshots(IHasResultsDir)}
     */
    private List<Snapshot> snapshots;

    /**
     * Instantiates a new CSVReader
     *
     * @param commitsDb
     * @param revisionsCsv the path of the revisionsFull.csv
     */
    public RevisionsCsvReader(CommitsDistanceDb commitsDb, File revisionsCsv) {
        this.commitsDb = commitsDb;
        this.revisionsCsv = revisionsCsv;
    }

    public int readCommitsThatModifyCFiles() {
        LOG.info("Reading all file changes in " + this.revisionsCsv.getAbsolutePath());
        this.commitsThatModifyCFiles = new LinkedHashSet<>();

        FileReader fr = null;
        BufferedReader br = null;
        final String cvsSplitBy = ",";
        //final SimpleDateFormat formatter = new SimpleDateFormat(OrderedRevisionsColumns.TIMESTAMP_FORMAT);
        int lineNo = 0;
        //int bugfixCount = 0;

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
                //int branch = Integer.parseInt(hunkInfo[OrderedRevisionsColumns.BRANCH.ordinal()]);
                //int positionInBranch = Integer.parseInt(hunkInfo[OrderedRevisionsColumns.POSITION.ordinal()]);
                String commitHash = hunkInfo[OrderedRevisionsColumns.COMMIT_ID.ordinal()];
                //boolean bugfix = false;
                //String comDateStr = hunkInfo[OrderedRevisionsColumns.TIMESTAMP.ordinal()];
                //Date comDate = formatter.parse(comDateStr);

                Commit commit = //new Commit(branch, positionInBranch, commitHash, comDate, bugfix);
                        commitsDb.findCommitOrDie(commitHash);
                commitsThatModifyCFiles.add(commit);
                //if (bugfix) {
                //    bugfixCount++;
                //}

                if (lineNo % 10000 == 0) {
                    LOG.debug("Processed " + lineNo + " lines of file changes ...");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading file " + this.revisionsCsv.getAbsolutePath(), e);
//        } catch (ParseException e) {
//            throw new RuntimeException("Error reading contents of " + this.revisionsCsv.getAbsolutePath(), e);
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
        LOG.info("Found " + commitsThatModifyCFiles.size() + " commits in "
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
    private static void assertRevisionsFullCsvHeaderIsSane(String headerLine) {
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

    public void computeAndPersistSnapshots(CreateSnapshotsConfig conf) {
        LOG.info("Computing snapshots");
        snapshots = new ArrayList<>();
        final int snapshotSize = conf.getSnapshotSize();

        final Consumer<List<Snapshot>> snapshotConsumer =
                new WriteSnapshotsToCsvFilesStrategy<>(this.commitsDb, conf)
                        .andThen(computedSnapshots -> snapshots.addAll(computedSnapshots));

        SnapshotCreatingCommitWalker<CreateSnapshotsConfig> snapshotsCreator =
                new SnapshotCreatingCommitWalker<>(commitsDb, conf, snapshotSize,
                        commitsThatModifyCFiles, snapshotConsumer);

        snapshotsCreator.processCommits();

        LOG.info("Successfully created " + snapshots.size() + " snapshots.");
    }

    public <TConfig extends IHasResultsDir & IHasSnapshotsDir> void readPrecomputedSnapshots(TConfig conf) {
        snapshots = SnapshotReader.readSnapshots(commitsDb, conf);
        LOG.info("Successfully read " + snapshots.size() + " snapshots.");
    }

    /**
     * The list of snapshots. Each snapshot contains exactly the same number of commits.
     *
     * @return The list of snapshots, in ascending order by date. Each snapshot contains at least one commit, i.e., the
     * maps are guaranteed to be non-empty.
     */
    public List<Snapshot> getSnapshots() {
        return snapshots;
    }

    /**
     * @return Snapshots, ordered according to the filter (if present) or by date (if no filter was given)
     */
    public Collection<Snapshot> getSnapshotsFiltered(IHasSnapshotFilter snapshotFilteringConfig) {
        Optional<List<Date>> filterDates = snapshotFilteringConfig.getSnapshotFilter();
        List<Snapshot> allSnapshots = this.getSnapshots();
        if (!filterDates.isPresent()) {
            return allSnapshots;
        }

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");

        Map<String, Snapshot> snapshotsByDate = new HashMap<>();
        for (Snapshot s : allSnapshots) {
            String dateString = df.format(s.getStartDate());
            snapshotsByDate.put(dateString, s);
        }

        List<Snapshot> result = new ArrayList<>();
        for (Date selectedDate : filterDates.get()) {
            String selectedDateString = df.format(selectedDate);
            Snapshot s = snapshotsByDate.get(selectedDateString);
            if (s == null) {
                LOG.warn("No such snapshot: " + selectedDateString);
            } else {
                result.add(s);
            }
        }

        return result;
    }

    /**
     * @return All commits that modify C files
     */
    public Set<Commit> getCommitsThatModifyCFiles() {
        return commitsThatModifyCFiles;
    }
}
