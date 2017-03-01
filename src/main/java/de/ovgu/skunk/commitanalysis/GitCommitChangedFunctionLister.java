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
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

public class GitCommitChangedFunctionLister implements Runnable {
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

    @Override
    public void run() {
        errors = 0;
        try {
            openRepo(config.repoDir);
        } catch (Exception e) {
            LOG.error("Error opening repository " + config.repoDir + ".", e);
            increaseErrorCount();
            throw new RuntimeException("Error opening repository " + config.repoDir, e);
        }
        try {
            listChangedFunctionsInSnapshot();
        } catch (RuntimeException t) {
            increaseErrorCount();
            throw t;
        } finally {
            try {
                closeRepo();
            } catch (RuntimeException t) {
                LOG.warn("Error closing repository " + config.repoDir + " (error will be ignored.)", t);
                increaseErrorCount();
            }
        }
    }

    public boolean errorsOccurred() {
        return errors > 0;
    }

    private void listChangedFunctionsInSnapshot() {
        CsvFileWriterHelper helper = newCsvFileWriterForSnapshot(snapshot);
        File outputFileDir = config.snapshotResultsDirForDate(snapshot.getSnapshotDate());
        File outputFile = new File(outputFileDir, ChangedSnapshotFunctionsColumns.FILE_BASENAME);
        helper.write(outputFile);
    }

    private CsvFileWriterHelper newCsvFileWriterForSnapshot(final Snapshot snapshot) {
        return new CsvFileWriterHelper() {
            CsvRowProvider<Map.Entry<Method, Integer>, Snapshot, ChangedSnapshotFunctionsColumns> csvRowProvider = ChangedSnapshotFunctionsColumns.newCsvRowProviderForSnapshot(snapshot);

            @Override
            protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
                csv.printRecord(csvRowProvider.headerRow());
                Consumer<Map.Entry<Method, Integer>> csvRowFromFunction = newThreadSafeFunctionToCsvWriter(csv, csvRowProvider);
                listChangedFunctions(snapshot.getCommitHashes(), csvRowFromFunction);
            }
        };
    }

    private Consumer<Map.Entry<Method, Integer>> newThreadSafeFunctionToCsvWriter(final CSVPrinter csv, final CsvRowProvider<Map.Entry<Method, Integer>, Snapshot, ChangedSnapshotFunctionsColumns> csvRowProvider) {
        return new Consumer<Map.Entry<Method, Integer>>() {
            @Override
            public void accept(Map.Entry<Method, Integer> functionChange) {
                Object[] rowForFunc = csvRowProvider.dataRow(functionChange);
                try {
                    synchronized (csv) {
                        csv.printRecord(rowForFunc);
                    }
                } catch (IOException ioe) {
                    throw new RuntimeException("IOException while writing row for changed function " +
                            functionChange.getKey(), ioe);
                }
            }
        };
    }

    private static class ThreadSafeFunctionChangeAggregator implements Consumer<Map.Entry<Method, Integer>> {
        private final Map<Method, Integer> aggregatedChanges = new HashMap<>();

        @Override
        public void accept(Map.Entry<Method, Integer> functionChangeEntry) {
            Method func = functionChangeEntry.getKey();
            Integer newChanges = functionChangeEntry.getValue();
            synchronized (aggregatedChanges) {
                Integer oldChanges = aggregatedChanges.get(func);
                if (oldChanges == null) {
                    aggregatedChanges.put(func, newChanges);
                } else {
                    aggregatedChanges.put(func, oldChanges + newChanges);
                }
            }
        }

        public Map<Method, Integer> getAggregatedFunctionChanges() {
            return aggregatedChanges;
        }
    }

    private void listChangedFunctions(Collection<String> commitIds, Consumer<Map.Entry<Method, Integer>> changedFunctionConsumer) {
        ThreadSafeFunctionChangeAggregator aggregatingConsumer = new ThreadSafeFunctionChangeAggregator();

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
                            listChangedFunctions(nextCommitId, aggregatingConsumer);
                        } catch (RuntimeException t) {
                            LOG.warn("Error processing commit ID " + nextCommitId, t);
                            increaseErrorCount();
                        }
                    }
                }
            };
        }

        executeWorkers(workers);

        for (Map.Entry<Method, Integer> e : aggregatingConsumer.getAggregatedFunctionChanges().entrySet()) {
            changedFunctionConsumer.accept(e);
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


    /**
     * <p>
     * Code partially taken from <a href=
     * 'http://stackoverflow.com/questions/19467305/using-the-jgit-how-can-i-retrieve-the-line-numbers-of-added-deleted-lines'>
     * Stackoverflow</a>
     * </p>
     *
     * @param commitId
     */
    private void listChangedFunctions(String commitId, Consumer<Map.Entry<Method, Integer>> changedFunctionConsumer) {
        LOG.info("Analyzing commit " + commitId);
        int linesAdded = 0;
        int linesDeleted = 0;
        int filesChanged = 0;
        RevWalk rw = null;
        DiffFormatter formatter = null;
        try {
            rw = new RevWalk(repo);
            RevCommit commit = rw.parseCommit(repo.resolve(commitId));
            ObjectId parentCommitId = commit.getParent(0).getId();
            RevCommit parent = rw.parseCommit(parentCommitId);
            formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
            formatter.setRepository(repo);
            formatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
            formatter.setDetectRenames(true);
            List<DiffEntry> diffs = formatter.scan(parent.getTree(), commit.getTree());
            LOG.info(parentCommitId.name() + " ... " + commitId);

            Set<String> aSideCFilePaths = new HashSet<>();
            for (DiffEntry diff : diffs) {
                String oldPath = diff.getOldPath();
                if (oldPath.endsWith(".c")) {
                    aSideCFilePaths.add(oldPath);
                }
            }

            LOG.debug("Parsing A-side functions");
            final Map<String, List<Method>> allASideFunctions = listFunctionsInFiles(parent, aSideCFilePaths);

            LOG.debug("Mappings edits to A-side function locations");
            for (DiffEntry diff : diffs) {
                String oldPath = diff.getOldPath();
                SortedMap<Method, Integer> changedFunctions = listChangedFunctions(formatter, diff, allASideFunctions);
                for (Map.Entry<Method, Integer> e : changedFunctions.entrySet()) {
                    int numEdits = e.getValue();
                    if (numEdits > 0) {
                        //Method func = e.getKey();
                        //System.out.println(commitId + "\t" + parentCommitId.name() + "\t" + numEdits + "\t" + oldPath + "\t" + func.functionSignatureXml);
                        changedFunctionConsumer.accept(e);
                    }
                }
            }
        } catch (IOException e1) {
            throw new RuntimeException(e1);
        } finally {
            try {
                if (formatter != null) formatter.release();
            } catch (RuntimeException e) {
                LOG.warn("Problem releasing diff formatter for commit " + commitId, e);
            } finally {
                formatter = null;
            }
            try {
                if (rw != null) rw.release();
            } catch (RuntimeException e) {
                LOG.warn("Problem releasing revision walker for commit " + commitId, e);
            } finally {
                rw = null;
            }
        }
    }

//    private void listFunctions(Map<String, List<Method>> changedFunctions, String prefix) {
//        for (Map.Entry<String, List<Method>> e : changedFunctions.entrySet()) {
//            LOG.debug(prefix + "\t" + e.getKey());
//            for (Method f : e.getValue()) {
//                LOG.debug(prefix + "\t" + f.start1 + ":" + f.end1 + "\t" + f.functionSignatureXml);
//            }
//        }
//    }

    private Map<String, List<Method>> listFunctionsInFiles(RevCommit commit, Set<String> filesPaths) throws IOException {
        if (filesPaths.isEmpty()) {
            return Collections.emptyMap();
        }
        FunctionLocationProvider functionLocationProvider = new FunctionLocationProvider(repo);
        return functionLocationProvider.listFunctionsInFiles(commit, filesPaths);
    }

    static class EditToFunctionLocMapper implements Consumer<Edit> {
        Map<Method, MutableInteger> editedFunctions;
        List<Method> functionsByOccurrence;

        public EditToFunctionLocMapper(List<Method> functions) {
            this.editedFunctions = new HashMap<>();
            this.functionsByOccurrence = new LinkedList<>(functions);
            for (Method f : functionsByOccurrence) {
                editedFunctions.put(f, new MutableInteger());
            }
        }

        @Override
        public void accept(Edit edit) {
            // NOTE, 2016-12-09, wf: To know which *existing* functions have been modified, we
            // only need to look at the "A"-side of the edit and can ignore the "B" side.
            // This is good because "A"-side line numbers are much easier to correlate with the
            // function locations we have than the "B"-side offsets.
            final int remBegin = edit.getBeginA();
            final int remEnd = edit.getEndA();
            for (Iterator<Method> fIter = functionsByOccurrence.iterator(); fIter.hasNext(); ) {
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
        }

        private void markFunctionEdit(Edit edit, Method f) {
            MutableInteger editCount = editedFunctions.get(f);
            editCount.value++;
            logEdit(f, edit);
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

        public SortedMap<Method, Integer> getEditedFunctions() {
            SortedMap<Method, Integer> result = new TreeMap<>(Method.COMP_BY_OCCURRENCE);
            for (Map.Entry<Method, MutableInteger> e : editedFunctions.entrySet()) {
                result.put(e.getKey(), e.getValue().value);
            }
            return result;
        }
    }

    private SortedMap<Method, Integer> listChangedFunctions(final DiffFormatter formatter, final DiffEntry diff, final Map<String, List<Method>> functionsByPath) throws IOException {
        final String oldPath = diff.getOldPath();
        final String newPath = diff.getNewPath();

        LOG.debug("--- " + oldPath);
        LOG.debug("+++ " + newPath);

        List<Method> functions = functionsByPath.get(oldPath);
        if (functions == null) {
            return Collections.emptySortedMap();
        }
        final EditToFunctionLocMapper editLocMapper = new EditToFunctionLocMapper(functions);

        for (Edit edit : formatter.toFileHeader(diff).toEditList()) {
            // linesDeleted += edit.getEndA() - edit.getBeginA();
            // linesAdded   += edit.getEndB() - edit.getBeginB();
            LOG.debug("- " + edit.getBeginA() + "," + edit.getEndA() +
                    " + " + edit.getBeginB() + "," + edit.getEndB());
            editLocMapper.accept(edit);
        }

        return editLocMapper.getEditedFunctions();
    }

    class BoundedByteArrayOutputStream extends OutputStream {
        private final int limit;
        private ByteArrayOutputStream delegate;

        public BoundedByteArrayOutputStream(int limit) {
            this.limit = limit;
            this.delegate = new ByteArrayOutputStream(limit);
        }

        @Override
        public void write(int b) throws IOException {
            synchronized (delegate) {
                dieIfNewSizeExceedsLimit(delegate.size() + 1);
                delegate.write(b);
            }
        }

        private void dieIfNewSizeExceedsLimit(int newSize) throws IOException {
            if ((newSize < 0) || (newSize > limit)) {
                throw new IOException("Limit exceeded.");
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (len < 0) {
                throw new IllegalArgumentException("len must be non-negative, not " + len);
            }
            synchronized (delegate) {
                dieIfNewSizeExceedsLimit(delegate.size() + len);
                delegate.write(b, off, len);
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            synchronized (delegate) {
                dieIfNewSizeExceedsLimit(delegate.size() + b.length);
                delegate.write(b);
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        public String toString(String charsetName) throws UnsupportedEncodingException {
            return delegate.toString(charsetName);
        }
    }

    private void openRepo(String repoDir) throws IOException {
        git = Git.open(new File(config.repoDir));
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
