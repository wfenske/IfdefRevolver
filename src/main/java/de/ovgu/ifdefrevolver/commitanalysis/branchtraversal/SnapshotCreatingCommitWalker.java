package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasSnapshotsDir;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.GitUtil;
import de.ovgu.ifdefrevolver.commitanalysis.IHasRepoDir;
import de.ovgu.ifdefrevolver.util.DateUtils;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.function.Consumer;

public class SnapshotCreatingCommitWalker<TConfig extends IHasResultsDir & IHasSnapshotsDir & IHasRepoDir> extends AbstractCommitWalker {
    private static Logger LOG = Logger.getLogger(SnapshotCreatingCommitWalker.class);

    private final int snapshotSize;
    private List<Snapshot> snapshots;
    private List<Commit> commitsInCurrentSnapshot;
    private Calendar lastSnapshotCal = null;
    private final TConfig config;
    private int numberOfRelevantCommitsInCurrentSnapshot;
    private final Set<Commit> commitsThatModifyCFiles;
    private final Consumer<List<Snapshot>> outputStrategy;

    public SnapshotCreatingCommitWalker(CommitsDistanceDb commitsDistanceDb, TConfig config, int snapshotSize, Set<Commit> commitsThatModifyCFiles, Consumer<List<Snapshot>> outputStrategy) {
        super(commitsDistanceDb);
        this.config = config;
        this.snapshotSize = snapshotSize;
        this.commitsThatModifyCFiles = commitsThatModifyCFiles;
        this.outputStrategy = outputStrategy;
    }

    @Override
    public void processCommits() {
        this.snapshots = new ArrayList<>();

        if (snapshotSize <= 0) {
            throw new IllegalArgumentException("Invalid snapshot size (should be >= 1): " + snapshotSize);
        }

        startNewSnapshot();
        super.processCommits();
    }

    @Override
    protected void onAllCommitsProcessed() {
        super.onAllCommitsProcessed();

        outputStrategy.accept(snapshots);

        final int numSnapshots = snapshots.size();
        final int commitsInSnapshots = snapshots.stream().map(s -> s.getCommits().size()).reduce(0, Integer::sum);
        final int discardedCommits = commitsInCurrentSnapshot.size();
        LOG.info("Collected " + commitsInSnapshots + " commit(s) in " + numSnapshots + " snapshot(s). " +
                "Discarded " + discardedCommits + " commit(s) since it/they did not fill up a whole snapshot.");
    }

    @Override
    protected void processCurrentCommit() {
        this.commitsInCurrentSnapshot.add(this.currentCommit);
        if (isCurrentCommitRelevant()) {
            this.numberOfRelevantCommitsInCurrentSnapshot++;
        }
        if ((this.numberOfRelevantCommitsInCurrentSnapshot % snapshotSize) == 0) {
            createSnapshotFromCurrentCommits();
            startNewSnapshot();
        }
    }

    private boolean isCurrentCommitRelevant() {
        return this.commitsThatModifyCFiles.contains(this.currentCommit);
    }

    private void startNewSnapshot() {
        this.commitsInCurrentSnapshot = new ArrayList<>();
        this.numberOfRelevantCommitsInCurrentSnapshot = 0;
    }

    private void createSnapshotFromCurrentCommits() {
        final Commit startCommit = this.commitsInCurrentSnapshot.get(0);
        Calendar snapshotDateCal = GitUtil.getAuthorDateOfCommit(config.getRepoDir(), startCommit.commitHash);
        snapshotDateCal = ensureSnapshotDatesAreUnique(snapshotDateCal);
        Date snapshotDate = snapshotDateCal.getTime();
        Snapshot s = new Snapshot(this.snapshots.size(), snapshotDate,
                new LinkedHashSet<>(this.commitsInCurrentSnapshot), config.snapshotDirForDate(snapshotDate));
        this.snapshots.add(s);
    }

    private Calendar ensureSnapshotDatesAreUnique(Calendar snapshotDateCal) {
        if (lastSnapshotCal == null) return snapshotDateCal;
        int advances = 0;
        while (!DateUtils.isAtLeastOneDayBefore(lastSnapshotCal, snapshotDateCal)) {
            snapshotDateCal.add(Calendar.DAY_OF_YEAR, 1);
            advances++;
        }
        if (advances > 0) {
            LOG.info("Advanced snapshot date by " + advances + " day(s) to ensure it is unique. New snapshot date is " + snapshotDateCal);
        }

        return snapshotDateCal;
    }

//    private void writeCurrentSnapshotToFile() {
//        CsvRowProvider<String, CommitWalkerContext, CommitWalkerSnapshotColumns> rowProvider;
//        CsvFileWriterHelper writer = new CsvFileWriterHelper() {
//            @Override
//            protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
//
//            }
//        };
//        File outputFile = new File(config)
//
//        writer.write(outputFile);
//    }
}
