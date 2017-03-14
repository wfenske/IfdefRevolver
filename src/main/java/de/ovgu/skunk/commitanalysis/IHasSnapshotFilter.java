package de.ovgu.skunk.commitanalysis;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Created by wfenske on 06.03.17.
 * <p>
 * For a configuration that supports analyzing/processing only specific snapshots of a project, instead of all of them
 */
public interface IHasSnapshotFilter {
    Optional<List<Date>> getSnapshotFilter();

    void setSnapshotFilter(List<Date> snapshotFilter);

    void validateSnapshotFilter();
}
