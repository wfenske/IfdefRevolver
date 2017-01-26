package de.ovgu.skunk.commitanalysis.changedfunctions;

import de.ovgu.skunk.commitanalysis.changedfunctions.main.Config;
import de.ovgu.skunk.detection.data.Method;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

public class GitCommitChangedFunctionLister implements Runnable {
    private static final Logger LOG = Logger.getLogger(GitCommitChangedFunctionLister.class);
    private Config config;
    private int errors = 0;
    private Git git = null;
    private Repository repo = null;

    public GitCommitChangedFunctionLister(Config config) {
        this.config = config;
    }

    @Override
    public void run() {
        if (true) {
            throw new RuntimeException("This is wrong: (is_absolute_uri should not be listed!)\n" +
                    "20:03:00  INFO Analyzing commit 0ce6568af0d6dffbefb78a787d108e1d95c366fe\n" +
                    "20:03:00  INFO 94be9638cfebeb6d049c85d7bd515d40a0d62b48 ... 0ce6568af0d6dffbefb78a787d108e1d95c366fe\n" +
                    "1\tmodules/mappers/mod_rewrite.c\tstatic unsigned is_absolute_uri(char *uri)\n" +
                    "2\tmodules/mappers/mod_rewrite.c\tstatic char *do_expand(char *input, rewrite_ctx *ctx, rewriterule_entry *entry)");
        }

        errors = 0;
        try {
            openRepo(config.repoDir);
        } catch (Exception e) {
            LOG.error("Error opening repository " + config.repoDir + ".", e);
            errors++;
            throw new RuntimeException("Error opening repository " + config.repoDir, e);
        }
        try {
            listChangedFunctions(config.commitIds);
        } catch (RuntimeException t) {
            errors++;
            throw t;
        } finally {
            try {
                closeRepo();
            } catch (RuntimeException t) {
                LOG.warn("Error closing repository " + config.repoDir + " (error will be ignored.)", t);
                errors++;
            }
        }
    }

    private void listChangedFunctions(Collection<String> commitIds) {
        int ixCommit = 1;
        final int numCommits = commitIds.size();
        for (String commitId : commitIds) {
            try {
                LOG.info("Processing commit " + (ixCommit++) + "/" + numCommits);
                listChangedFunctions(commitId);
            } catch (RuntimeException t) {
                LOG.warn("Error processing commit ID " + commitId, t);
                errors++;
            }
        }
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
    private void listChangedFunctions(String commitId) {
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
            final Map<String, List<Method>> allASideFunctions = listFunctionsInChangedFiles(parent, aSideCFilePaths);

            LOG.debug("Mappings edits to A-side function locations");
            for (DiffEntry diff : diffs) {
                String oldPath = diff.getOldPath();
                SortedMap<Method, Integer> changedFunctions = listChangedFunctions(formatter, diff, allASideFunctions);
                for (Map.Entry<Method, Integer> e : changedFunctions.entrySet()) {
                    int edits = e.getValue();
                    if (edits > 0) {
                        Method func = e.getKey();
                        System.out.println(edits + "\t" + oldPath + "\t" + func.functionSignatureXml);
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

    private void listFunctions(Map<String, List<Method>> changedFunctions, String prefix) {
        for (Map.Entry<String, List<Method>> e : changedFunctions.entrySet()) {
            LOG.debug(prefix + "\t" + e.getKey());
            for (Method f : e.getValue()) {
                LOG.debug(prefix + "\t" + f.start + ":" + f.end + "\t" + f.functionSignatureXml);
            }
        }
    }

    private Map<String, List<Method>> listFunctionsInChangedFiles(RevCommit commit, Set<String> changedFilesPaths) throws IOException {
        if (changedFilesPaths.isEmpty()) {
            return Collections.emptyMap();
        }
        FunctionLocationProvider functionLocationProvider = new FunctionLocationProvider(repo);
        return functionLocationProvider.listFunctionsInChangedFiles(commit, changedFilesPaths);
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
                } else if (f.end < remBegin) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Removing " + f + ": no future edits to be expected.");
                    }
                    fIter.remove();
                }
            }
        }

        private void markFunctionEdit(Edit edit, Method f) {
            MutableInteger editCount = editedFunctions.get(f);
            editCount.value++;
            logEdit(f, edit);
        }

        private void logEdit(Method f, Edit edit) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Detected edit to " + f + ": " + edit.getBeginA() + "," + edit.getEndA());
            }
        }

        private boolean editOverlaps(Method func, final int editBegin, final int editEnd) {
            int fBegin = func.start;
            int fEnd = func.end;
            return ((editBegin <= fEnd) && (editEnd >= fBegin));
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

    private void xlistChangedFunctions(String commitId) {
        Iterable<RevCommit> commits;
        try {
            commits = git.log().add(repo.resolve(commitId)).call();
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Failed to resolve commit " + commitId, e);
        }
        for (RevCommit jgitCommit : commits) {
            // String msg = jgitCommit.getFullMessage().trim();
            // String hash = jgitCommit.getName().toString();
            long epoch = jgitCommit.getCommitTime();
            final int parentCount = jgitCommit.getParentCount();
            // String parent = (parentCount > 0) ?
            // jgitCommit.getParent(0).getName().toString() : "";
            GregorianCalendar date = new GregorianCalendar();
            date.setTime(new Date(epoch * 1000L));
            boolean merge = false;
            if (parentCount > 1) merge = true;
            if (merge) {
                LOG.info("Ignoring merge commit " + commitId);
                continue;
            }
            List<DiffEntry> diffsForTheCommit;
            try {
                diffsForTheCommit = diffsForTheCommit(jgitCommit);
            } catch (RevisionSyntaxException | IOException e) {
                throw new RuntimeException("Exception gettings diffs for commit " + commitId, e);
            }
            if (diffsForTheCommit.size() > config.maxNumberOfFilesPerCommit) {
                LOG.warn("commit " + commitId + " has more than files than the limit");
                throw new RuntimeException("commit " + commitId + " too big, sorry");
            }
            System.out.print(commitId);
            System.out.println(":");
            analyzeDiffsForCommit(diffsForTheCommit);
            break;
        }
    }

    void analyzeDiffsForCommit(List<DiffEntry> diffsForTheCommit) {
        for (DiffEntry diff : diffsForTheCommit) {
            final ChangeType changeType = diff.getChangeType();
            String oldPath = diff.getOldPath();
            String newPath = diff.getNewPath();
            String diffText = "";
            String sc = "";
            if (changeType != ChangeType.DELETE) {
                diffText = getDiffText(diff);
                sc = getSourceCode(diff);
            }
            System.out.print("---\t");
            System.out.println(oldPath);
            System.out.print("+++\t");
            System.out.println(newPath);
            // System.out.println(":");
            System.out.println(diffText);
        }
    }

    private String getSourceCode(DiffEntry diff) {
        try {
            ObjectReader reader = repo.newObjectReader();
            final ObjectLoader loader = reader.open(diff.getNewId().toObjectId());
            byte[] bytes;
            try {
                bytes = loader.getCachedBytes(config.maxSizeOfDiffSource);
            } catch (LargeObjectException loe) {
                LOG.warn("diff source for " + diff.getNewPath() + " too big.", loe);
                return "-- TOO BIG --";
            }
            return new String(bytes, "utf-8");
        } catch (Throwable e) {
            LOG.warn("Failed to obtain source code for diff of " + diff.getNewPath(), e);
            return "-- ERROR GETTING SOURCE OF NEW OBJECT --";
        }
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

    private String getDiffText(DiffEntry diff) {
        DiffFormatter df2 = null;
        BoundedByteArrayOutputStream out = null;
        try {
            out = new BoundedByteArrayOutputStream(config.maxSizeOfADiff);
            df2 = new DiffFormatter(out);
            df2.setRepository(repo);
            df2.format(diff);
            String diffText = out.toString("UTF-8");
            return diffText;
        } catch (IOException ioe) {
            LOG.warn("diff for " + diff.getNewPath() + " too big.", ioe);
            return "-- TOO BIG --";
        } catch (Throwable e) {
            LOG.warn("Failed to obtain diff for " + diff.getNewPath(), e);
            return "-- ERROR GETTING DIFF TEXT --";
        } finally {
            try {
                if (df2 != null) df2.release();
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ioe) {
                        // Don't care.
                    }
                }
            }
        }
    }

    private List<DiffEntry> diffsForTheCommit(RevCommit commit)
            throws RevisionSyntaxException, AmbiguousObjectException, IncorrectObjectTypeException, IOException {
        AnyObjectId currentCommit = repo.resolve(commit.getName());
        AnyObjectId parentCommit = commit.getParentCount() > 0 ? repo.resolve(commit.getParent(0).getName()) : null;
        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setBinaryFileThreshold(config.binaryFileSizeThresholdInKb);
        df.setRepository(repo);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setDetectRenames(true);
        List<DiffEntry> diffs = null;
        if (parentCommit == null) {
            RevWalk rw = new RevWalk(repo);
            diffs = df.scan(new EmptyTreeIterator(),
                    new CanonicalTreeParser(null, rw.getObjectReader(), commit.getTree()));
            rw.release();
        } else {
            diffs = df.scan(parentCommit, currentCommit);
        }
        df.release();
        return diffs;
    }

    /**
     * @return Number of errors that have occurred during {@link #run()}. If
     * everything went fine, this returns <code>0</code>.
     */
    public int errors() {
        return errors;
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
