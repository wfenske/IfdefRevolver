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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class SnapshotChangedFunctionLister {
    private static final Logger LOG = Logger.getLogger(SnapshotChangedFunctionLister.class);
    private ListChangedFunctionsConfig config;
    private Snapshot snapshot;
    private int errors = 0;
    private Git git = null;
    private Repository repo = null;

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
        File outputFileDir = config.snapshotResultsDirForDate(snapshot.getSnapshotDate());
        File outputFile = new File(outputFileDir, FunctionChangeHunksColumns.FILE_BASENAME);
        CsvFileWriterHelper helper = newCsvFileWriterForSnapshot(snapshot, outputFile);
        helper.write(outputFile);
        return outputFile;
    }

    private CsvFileWriterHelper newCsvFileWriterForSnapshot(final Snapshot snapshot, final File outputFile) {
        final String uncaughtExceptionErrorMessage = "Uncaught exception while listing changing functions in snapshot " + snapshot + ". Deleting output file " + outputFile.getAbsolutePath();
        final String fileDeleteFailedErrorMessage = "Failed to delete output file " + outputFile.getAbsolutePath() + ". Must be deleted manually.";

        return new CsvFileWriterHelper() {
            CsvRowProvider<FunctionChangeHunk, Snapshot, FunctionChangeHunksColumns> csvRowProvider = FunctionChangeHunksColumns.newCsvRowProviderForSnapshot(snapshot);

            @Override
            protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
                csv.printRecord(csvRowProvider.headerRow());
                Consumer<FunctionChangeHunk> csvRowFromFunction = newThreadSafeFunctionToCsvWriter(csv, csvRowProvider);
                try {
                    listChangedFunctions(snapshot.getCommitHashes(), csvRowFromFunction);
                } catch (UncaughtWorkerThreadException ex) {
                    increaseErrorCount();
                    LOG.error(uncaughtExceptionErrorMessage, ex);
                    if (outputFile.delete()) {
                    } else {
                        LOG.error(fileDeleteFailedErrorMessage);
                    }
                }
            }
        };
    }

    private Consumer<FunctionChangeHunk> newThreadSafeFunctionToCsvWriter(final CSVPrinter csv, final CsvRowProvider<FunctionChangeHunk, Snapshot, FunctionChangeHunksColumns> csvRowProvider) {
        return functionChange -> {
            if (functionChange.deletesFunction()) {
                LOG.debug("Ignoring change " + functionChange + ". The whole function is deleted (probably moved someplace else).");
                return;
            }
            ChangeHunk hunk = functionChange.getHunk();
            if ((hunk.getLinesAdded() == 0) && (hunk.getLinesDeleted() == 0)) {
                LOG.debug("Ignoring change " + functionChange + ". No lines are added or deleted.");
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

    private void listChangedFunctions(Collection<String> commitIds, Consumer<FunctionChangeHunk> changedFunctionConsumer) throws UncaughtWorkerThreadException {
        Iterator<String> commitIdIter = commitIds.iterator();
        TerminableThread[] workers = new TerminableThread[config.getNumThreads()];
        final List<Throwable> uncaughtWorkerThreadException = new ArrayList<>(workers.length);

        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread th, Throwable ex) {
                increaseErrorCount();
                for (TerminableThread wt : workers) {
                    wt.requestTermination();
                }
                synchronized (uncaughtWorkerThreadException) {
                    uncaughtWorkerThreadException.add(ex);
                }
            }
        };

        for (int iWorker = 0; iWorker < workers.length; iWorker++) {
            TerminableThread t = new TerminableThread() {
                @Override
                public void run() {
                    while (!terminationRequested) {
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
            t.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            workers[iWorker] = t;
        }

        executeWorkers(workers);

        for (Throwable ex : uncaughtWorkerThreadException) {
            throw new UncaughtWorkerThreadException(ex);
        }
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
