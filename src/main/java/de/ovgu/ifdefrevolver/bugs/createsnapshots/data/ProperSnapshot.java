/**
 *
 */
package de.ovgu.ifdefrevolver.bugs.createsnapshots.data;

import org.apache.log4j.Logger;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Represents a proper snapshot, which is a non-empty collection of
 * {@link Commit} instances, ordered in ascending order by date. A snapshot
 * comprises a non-empty number of bug-fix commits.
 *
 * @author wfenske
 */
public class ProperSnapshot implements ISnapshot {
    private static Logger log = Logger.getLogger(ProperSnapshot.class);

    private final SortedMap<Commit, Set<FileChange>> commits;
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
    private final int sortIndex;

    public ProperSnapshot(SortedMap<Commit, Set<FileChange>> commits, int sortIndex) {
        if (commits == null) {
            throw new NullPointerException("Attempt to instantiate proper snapshot with null for commits.");
        }

        if (commits.isEmpty()) {
            throw new NullPointerException("Attempt to instantiate proper snapshot with an empty map of commits.");
        }

        this.commits = commits;
        this.sortIndex = sortIndex;
    }

    private Commit representativeCommit() {
        return commits.firstKey();
    }

    @Override
    public String revisionHash() {
        Commit c = representativeCommit();
        return c.getHash();
    }

    @Override
    public Date revisionDate() {
        Commit c = representativeCommit();
        return c.getDate();
    }

    @Override
    public String revisionDateString() {
        Date d = revisionDate();
        synchronized (dateFormatter) {
            return dateFormatter.format(d);
        }
    }

    @Override
    public Date startDate() {
        return commits.firstKey().getDate();
    }

    @Override
    public Date endDate() {
        return commits.lastKey().getDate();
    }

    /**
     * @return A non-empty map of commits, sorted in ascending order.
     */
    @Override
    public SortedMap<Commit, Set<FileChange>> getCommits() {
        return commits;
    }

    @Override
    public Collection<File> computeChangedFiles(Collection<File> filesInNextSnapshotCheckout, ISnapshot nextSnapshot) {
        Map<String, File> filesInNextCheckoutByBaseName = new HashMap<>();
        for (File file : filesInNextSnapshotCheckout) {
            String basename = file.getName();
            File previousFile = filesInNextCheckoutByBaseName.put(basename, file);
            if (previousFile != null) {
                throw new AssertionError("Error copying changed files for snapshot " + nextSnapshot.revisionDateString()
                        + " there are two files with the basename " + basename + ": " + file.getAbsolutePath() + " and "
                        + previousFile.getAbsolutePath());
            }
        }

        SortedMap<Commit, Set<FileChange>> nextCommits = nextSnapshot.getCommits();
        Commit firstNextCommit = nextCommits.firstKey();
        Set<FileChange> changesForFirstNextCommit = nextCommits.get(firstNextCommit);

        SortedMap<Commit, Set<FileChange>> relevantChanges = new TreeMap<>();
        relevantChanges.putAll(getCommits());
        relevantChanges.remove(getCommits().firstKey());
        relevantChanges.put(firstNextCommit, changesForFirstNextCommit);

        Set<String> changedFileNames = new HashSet<>();
        for (Set<FileChange> fileChanges : relevantChanges.values()) {
            for (FileChange fileChange : fileChanges) {
                String filename = fileChange.getFilename();
                changedFileNames.add(filename);
            }
        }

        Map<String, File> changedFilesByBasename = new HashMap<>();
        for (String basename : changedFileNames) {
            if (changedFilesByBasename.containsKey(basename)) {
                continue;
            }

            File changedFile = filesInNextCheckoutByBaseName.get(basename);
            if (changedFile == null) {
                log.debug("File was deleted between snapshots " + this.revisionDateString() + " and "
                        + nextSnapshot.revisionDateString() + ": " + basename);
            } else {
                log.debug("File was changed between snapshots " + this.revisionDateString() + " and "
                        + nextSnapshot.revisionDateString() + ": " + basename);
                changedFilesByBasename.put(basename, changedFile);
            }
        }

        Collection<File> changedFiles = new ArrayList<>(changedFilesByBasename.values());
        return changedFiles;

    }

    @Override
    public String toString() {
        final String fromDateString;
        final String toDateString;

        synchronized (dateFormatter) {
            fromDateString = dateFormatter.format(startDate());
            toDateString = dateFormatter.format(endDate());
        }

        return String.format("%s [index: %d, from=%s, to=%s]",
                this.getClass().getSimpleName(), sortIndex, fromDateString, toDateString);
    }

    public int getSortIndex() {
        return sortIndex;
    }
}
