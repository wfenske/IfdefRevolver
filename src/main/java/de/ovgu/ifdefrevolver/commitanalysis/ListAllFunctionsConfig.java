package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.main.ProjectInformationConfig;

import java.io.File;
import java.util.Date;

/**
 * Created by wfenske on 08.02.17.
 */
public class ListAllFunctionsConfig extends ProjectInformationConfig {
    public File allFunctionsInSnapshotCsv(Date snapshotDate) {
        return new File(snapshotResultsDirForDate(snapshotDate), "all_functions.csv");
    }
}
