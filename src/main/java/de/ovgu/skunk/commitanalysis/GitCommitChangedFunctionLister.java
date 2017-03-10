package de.ovgu.skunk.commitanalysis;

import de.ovgu.skunk.bugs.correlate.data.Snapshot;
import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.detection.output.CsvFileWriterHelper;
import de.ovgu.skunk.detection.output.CsvRowProvider;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public class GitCommitChangedFunctionLister {
    private static final Logger LOG = Logger.getLogger(GitCommitChangedFunctionLister.class);
    private ListChangedFunctionsConfig config;
    private Snapshot snapshot;
    private int errors = 0;
    private Git git = null;
    private Repository repo = null;

    public GitCommitChangedFunctionLister(ListChangedFunctionsConfig config, Snapshot snapshot) {
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
        CsvFileWriterHelper helper = newCsvFileWriterForSnapshot(snapshot);
        File outputFileDir = config.snapshotResultsDirForDate(snapshot.getSnapshotDate());
        File outputFile = new File(outputFileDir, FunctionChangeHunksColumns.FILE_BASENAME);
        helper.write(outputFile);
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
                            SingleGitCommitChangedFunctionLister lister = new SingleGitCommitChangedFunctionLister(repo, nextCommitId, changedFunctionConsumer);
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

    static class SingleGitCommitChangedFunctionLister {
        private final Repository repo;
        private final String commitId;
        private final Consumer<FunctionChangeHunk> changedFunctionConsumer;
        private DiffFormatter formatter = null;
        /**
         * All the functions defined in the A-side files of the files that the diffs within this commit modify
         */
        private Map<String, List<Method>> allASideFunctions;

        public SingleGitCommitChangedFunctionLister(Repository repo, String commitId, Consumer<FunctionChangeHunk> changedFunctionConsumer) {
            this.repo = repo;
            this.commitId = commitId;
            this.changedFunctionConsumer = changedFunctionConsumer;
        }

        /**
         * <p>
         * Code partially taken from <a href=
         * 'http://stackoverflow.com/questions/19467305/using-the-jgit-how-can-i-retrieve-the-line-numbers-of-added-deleted-lines'>
         * Stackoverflow</a>
         * </p>
         */
        public void listChangedFunctions() {
            LOG.info("Analyzing commit " + commitId);
            RevWalk rw = null;
            try {
                rw = new RevWalk(repo);
                RevCommit commit = rw.parseCommit(repo.resolve(commitId));
                ObjectId parentCommitId = commit.getParent(0).getId();
                RevCommit parent = rw.parseCommit(parentCommitId);
                formatter = getDiffFormatterInstance();
                List<DiffEntry> diffs = formatter.scan(parent.getTree(), commit.getTree());
                LOG.debug(parentCommitId.name() + " ... " + commitId);

                Set<String> aSideCFilePaths = getFilenamesOfCFilesModifiedByDiffsASides(diffs);
                allASideFunctions = listAllFunctionsInModifiedFiles(parent, aSideCFilePaths);

                mapEditsToASideFunctionLocations(diffs);
            } catch (IOException ioe) {
                throw new RuntimeException("I/O exception parsing files changed by commit " + commitId, ioe);
            } finally {
                releaseFormatter();
                releaseRevisionWalker(rw);
            }
        }

        private void releaseRevisionWalker(RevWalk rw) {
            try {
                if (rw != null) rw.release();
            } catch (RuntimeException e) {
                LOG.warn("Problem releasing revision walker for commit " + commitId, e);
            }
        }

        private void releaseFormatter() {
            try {
                if (formatter != null) formatter.release();
            } catch (RuntimeException e) {
                LOG.warn("Problem releasing diff formatter for commit " + commitId, e);
            }
        }

        private void mapEditsToASideFunctionLocations(List<DiffEntry> diffs) throws IOException {
            LOG.debug("Mappings edits to A-side function locations");
            for (DiffEntry diff : diffs) {
                listChangedFunctions(diff);
            }
        }

        private Set<String> getFilenamesOfCFilesModifiedByDiffsASides(List<DiffEntry> diffs) {
            Set<String> aSideCFilePaths = new HashSet<>();
            for (DiffEntry diff : diffs) {
                String oldPath = diff.getOldPath();
                if (oldPath.endsWith(".c")) {
                    aSideCFilePaths.add(oldPath);
                }
            }
            return aSideCFilePaths;
        }

        private DiffFormatter getDiffFormatterInstance() {
            DiffFormatter formatter;
            formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
            formatter.setRepository(repo);
            formatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
            formatter.setDetectRenames(true);
            return formatter;
        }

        private Map<String, List<Method>> listAllFunctionsInModifiedFiles(RevCommit stateBeforeCommit, Set<String> modifiedFiles) throws IOException {
            LOG.debug("Parsing all A-side functions");
            if (modifiedFiles.isEmpty()) {
                return Collections.emptyMap();
            }

            FunctionLocationProvider functionLocationProvider = new FunctionLocationProvider(repo);
            return functionLocationProvider.listFunctionsInFiles(commitId, stateBeforeCommit, modifiedFiles);
        }

        private void listChangedFunctions(final DiffEntry diff) throws IOException {
            final String oldPath = diff.getOldPath();
            final String newPath = diff.getNewPath();

            LOG.debug("--- " + oldPath);
            LOG.debug("+++ " + newPath);

            List<Method> functions = allASideFunctions.get(oldPath);
            if (functions == null) {
                return;
            }

            EditToFunctionLocMapper editLocMapper = new EditToFunctionLocMapper(oldPath, functions);

            for (Edit edit : formatter.toFileHeader(diff).toEditList()) {
                LOG.debug("- " + edit.getBeginA() + "," + edit.getEndA() +
                        " + " + edit.getBeginB() + "," + edit.getEndB());
                editLocMapper.accept(edit);
            }
        }

        class EditToFunctionLocMapper implements Consumer<Edit> {
            final List<Method> functionsInOldPathByOccurrence;
            /**
             * A-side file path of the file being modified
             */
            final String oldPath;
            /**
             * Number of the change hunk within the file for which this mapper has been created.  It is increased each time {@link #accept(Edit)} is called.
             */
            int numHunkInFile = 0;

            public EditToFunctionLocMapper(String oldPath, Collection<Method> functionsInOldPathByOccurrence) {
                this.oldPath = oldPath;
                this.functionsInOldPathByOccurrence = new LinkedList<>(functionsInOldPathByOccurrence);
            }

            @Override
            public void accept(Edit edit) {
                // NOTE, 2016-12-09, wf: To know which *existing* functions have been modified, we
                // only need to look at the "A"-side of the edit and can ignore the "B" side.
                // This is good because "A"-side line numbers are much easier to correlate with the
                // function locations we have than the "B"-side offsets.
                try {
                    final int remBegin = edit.getBeginA();
                    final int remEnd = edit.getEndA();
                    for (Iterator<Method> fIter = functionsInOldPathByOccurrence.iterator(); fIter.hasNext(); ) {
                        Method f = fIter.next();
                        if (editOverlaps(f, remBegin, remEnd)) {
                            markFunctionEdit(edit, f);
                        } else if (f.end1 < remBegin) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("No future edits possible for " + f);
                            }
                            fIter.remove();
                        } else if (f.start1 > remEnd) {
                            LOG.debug("Suspending search for modified functions at " + f);
                            break;
                        }
                    }
                } finally {
                    numHunkInFile++;
                }
            }

            private void markFunctionEdit(Edit edit, Method f) {
                ChangeHunk hunk = hunkFromEdit(edit);
                FunctionChangeHunk fHunk = new FunctionChangeHunk(f, hunk);
                changedFunctionConsumer.accept(fHunk);
                logEdit(f, edit);
            }

            private ChangeHunk hunkFromEdit(Edit edit) {
                int linesDeleted = edit.getLengthA();
                int linesAdded = edit.getLengthB();
                return new ChangeHunk(commitId, oldPath, this.numHunkInFile, linesDeleted, linesAdded);
            }

            private void logEdit(Method f, Edit edit) {
                if (LOG.isDebugEnabled() || LOG.isTraceEnabled()) {
                    int editBegin = edit.getBeginA();
                    int editEnd = edit.getEndA();
                    LOG.debug("Detected edit to " + f + ": " + editBegin + "," + editEnd);
                    String[] lines = f.getSourceCode().split("\n");
                    int fBegin = (f.start1 - 1);
                    int adjustedBegin = Math.max(editBegin - fBegin, 0);
                    int adjustedEnd = Math.min(editEnd - fBegin, lines.length);
                    for (int iLine = adjustedBegin; iLine < adjustedEnd; iLine++) {
                        LOG.debug("- " + lines[iLine]);
                    }
                    LOG.trace(f.sourceCodeWithLineNumbers());
                }
            }

            private boolean editOverlaps(Method func, final int editBegin, final int editEnd) {
                // NOTE, 2017-02-04, wf: We subtract 1 from the function's line
                // numbers because function line numbers are 1-based, whereas edit
                // line numbers are 0-based.
                int fBegin = func.start1 - 1;
                int fEnd = func.end1 - 1;
                return ((editBegin < fEnd) && (editEnd >= fBegin));
            }
        }
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
