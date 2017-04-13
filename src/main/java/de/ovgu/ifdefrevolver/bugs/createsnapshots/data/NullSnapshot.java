/**
 *
 */
package de.ovgu.ifdefrevolver.bugs.createsnapshots.data;

import java.io.File;
import java.util.*;

/**
 * @author wfenske
 */
public class NullSnapshot implements ISnapshot {

    private static final NullSnapshot INSTANCE = new NullSnapshot();

    /**
     * @return An instance of the {@link NullSnapshot}
     */
    public static final NullSnapshot getInstance() {
        return INSTANCE;
    }

    private NullSnapshot() {
        // Not meant to be publically instanciated
    }

    @Override
    public Date revisionDate() {
        return null;
    }

    @Override
    public String revisionDateString() {
        return null;
    }

    @Override
    public SortedMap<Commit, Set<FileChange>> getCommits() {
        return Collections.emptySortedMap();
    }

    @Override
    public Date startDate() {
        return null;
    }

    @Override
    public Date endDate() {
        return null;
    }

    @Override
    public String revisionHash() {
        return null;
    }

    @Override
    public Collection<File> computeChangedFiles(Collection<File> filesInNextSnapshotCheckout, ISnapshot nextSnapshot) {
        return filesInNextSnapshotCheckout;
    }

    @Override
    public String toString() {
        return "NullSnapshot";
    }

}
