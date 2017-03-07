package de.ovgu.skunk.commitanalysis;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Created by wfenske on 06.03.17.
 * <p>
 * For a configuration that supports analyzing/processing only specific snapshots of a project, instead of all of them
 */
public interface IHasSnapshots {
    Optional<List<Date>> getSnapshots();

    void setSnapshots(List<Date> snapshots);

    void validateSnapshots();
}
