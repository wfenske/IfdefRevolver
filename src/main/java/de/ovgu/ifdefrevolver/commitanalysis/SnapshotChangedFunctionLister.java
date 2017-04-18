package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.skunk.detection.output.CsvFileWriterHelper;
import de.ovgu.skunk.detection.output.CsvRowProvider;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;

public class SnapshotChangedFunctionLister {
    private static final Logger LOG = Logger.getLogger(SnapshotChangedFunctionLister.class);
    private ListChangedFunctionsConfig config;
    private Snapshot snapshot;
    private int errors = 0;
    private Git git = null;
    private Repository repo = null;
    private File outputFile = null;

    public SnapshotChangedFunctionLister(ListChangedFunctionsConfig config, Snapshot snapshot) {
        this.config = config;
        this.snapshot = snapshot;
    }

    /**
     * @return Name of the CSV file in which the results will be saved
     */
    public File listChangedFunctions() {
        errors = 0;
        try {
            openRepo(config.getRepoDir());
        } catch (Exception e) {
            LOG.error("Error opening repository " + config.getRepoDir() + ".", e);
            increaseErrorCount();
            throw new RuntimeException("Error opening repository " + config.getRepoDir(), e);
        }
        try {
            return listChangedFunctionsInSnapshot();
        } catch (RuntimeException t) {
            increaseErrorCount();
            throw t;
        } finally {
            try {
                closeRepo();
            } catch (RuntimeException t) {
                LOG.warn("Error closing repository " + config.getRepoDir() + " (error will be ignored.)", t);
                increaseErrorCount();
            }
        }
    }

    public boolean errorsOccurred() {
        return errors > 0;
    }

    private File listChangedFunctionsInSnapshot() {
        this.outputFile = null;
        try {
            CsvFileWriterHelper helper = newCsvFileWriterForSnapshot(snapshot);
            File outputFileDir = config.snapshotResultsDirForDate(snapshot.getSnapshotDate());
            this.outputFile = new File(outputFileDir, FunctionChangeHunksColumns.FILE_BASENAME);
            helper.write(outputFile);
        } catch (OutOfMemoryError ooe) {
            LOG.warn("Out of memory while analyzing snapshot " + snapshot, ooe);
            if (this.outputFile != null) {
                if (this.outputFile.delete()) {
                    this.outputFile = null;
                } else {
                    LOG.warn("Failed to delete output file " + this.outputFile.getAbsolutePath()
                            + " after out of memory exception.");
                }
            }
        }
        return outputFile;
    }

    private CsvFileWriterHelper newCsvFileWriterForSnapshot(final Snapshot snapshot) {
        return new CsvFileWriterHelper() {
            CsvRowProvider<FunctionChangeHunk, Snapshot, FunctionChangeHunksColumns> csvRowProvider = FunctionChangeHunksColumns.newCsvRowProviderForSnapshot(snapshot);

            @Override
            protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
                csv.printRecord(csvRowProvider.headerRow());
                Consumer<FunctionChangeHunk> csvRowFromFunction = newThreadSafeFunctionToCsvWriter(csv, csvRowProvider);
                listChangedFunctions(snapshot.getCommitHashes(), csvRowFromFunction);
            }
        };
    }

    private Consumer<FunctionChangeHunk> newThreadSafeFunctionToCsvWriter(final CSVPrinter csv, final CsvRowProvider<FunctionChangeHunk, Snapshot, FunctionChangeHunksColumns> csvRowProvider) {
        return functionChange -> {
            if (functionChange.deletesFunction()) {
                LOG.info("Ignoring change " + functionChange + ". The whole function is deleted (probably moved someplace else).");
                return;
            }
            ChangeHunk hunk = functionChange.getHunk();
            if ((hunk.getLinesAdded() == 0) && (hunk.getLinesDeleted() == 0)) {
                LOG.info("Ignoring change " + functionChange + ". No lines are added or deleted.");
                return;
            }
            Object[] rowForFunc = csvRowProvider.dataRow(functionChange);
            try {
                synchronized (csv) {
                    csv.printRecord(rowForFunc);
                }
            } catch (IOException ioe) {
                throw new RuntimeException("IOException while writing row for changed function " +
                        functionChange, ioe);
            }
        };
    }

    private void listChangedFunctions(Collection<String> commitIds, Consumer<FunctionChangeHunk> changedFunctionConsumer) {
        Iterator<String> commitIdIter = commitIds.iterator();
        Thread[] workers = new Thread[config.getNumThreads()];

        for (int iWorker = 0; iWorker < workers.length; iWorker++) {
            workers[iWorker] = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        final String nextCommitId;
                        synchronized (commitIdIter) {
                            if (!commitIdIter.hasNext()) {
                                break;
                            }
                            nextCommitId = commitIdIter.next();
                        }
                        try {
                            //LOG.info("Processing file " + (ixFile++) + "/" + numFiles);
                            CommitChangedFunctionLister lister = new CommitChangedFunctionLister(repo, nextCommitId, changedFunctionConsumer);
                            lister.listChangedFunctions();
                        } catch (RuntimeException t) {
                            LOG.warn("Error processing commit ID " + nextCommitId + ". Processing will continue with the remaining IDs.", t);
                            increaseErrorCount();
                        }
                    }
                }
            };
        }

        executeWorkers(workers);
    }

    private void executeWorkers(Thread[] workers) {
        for (int iWorker = 0; iWorker < workers.length; iWorker++) {
            workers[iWorker].start();
        }

        for (int iWorker = 0; iWorker < workers.length; iWorker++) {
            try {
                workers[iWorker].join();
            } catch (InterruptedException e) {
                LOG.warn("Interrupted while waiting for changed function lister thread to finish.", e);
            }
        }
    }

    private synchronized int increaseErrorCount() {
        return errors++;
    }

    private void openRepo(String repoDir) throws IOException {
        git = Git.open(new File(config.getRepoDir()));
        repo = git.getRepository();
    }

    private void closeRepo() {
        if (repo != null) {
            try {
                repo.close();
            } finally {
                try {
                    if (git != null) {
                        try {
                            git.close();
                        } finally {
                            git = null;
                        }
                    }
                } finally {
                    repo = null;
                }
            }
        }
    }
}
