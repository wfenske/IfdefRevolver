package de.ovgu.skunk.commitanalysis;

import de.ovgu.skunk.bugs.createsnapshots.main.CreateSnapshots;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by wfenske on 08.02.17.
 */
public class ListAllFunctionsConfig {
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    //private String resultsDir = null;
    //public static final String DEFAULT_RESULTS_DIR_NAME = CreateSnapshots.Config.DEFAULT_RESULTS_DIR_NAME;

    String snapshotsDir = null;
    public static final String DEFAULT_SNAPSHOTS_DIR_NAME = CreateSnapshots.Config.DEFAULT_SNAPSHOTS_DIR_NAME;

    String project = null;

    @Deprecated
    List<String> filenames;

    //public File projectResultsDir() {
    //    return new File(resultsDir, project);
    //}

    public File projectSnapshotsDir() {
        return new File(snapshotsDir, project);
    }

    public synchronized File tmpSnapshotDir(Date snapshotDate) {
        return new File(projectSnapshotsDir(), dateFormatter.format(snapshotDate));
    }

    //public synchronized File resultsSnapshotDir(Date snapshotDate) {
    //    return new File(projectResultsDir(), dateFormatter.format(snapshotDate));
    //}

    //public File projectInfoCsv() {
    //    return new File(projectResultsDir(), "projectInfo.csv");
    //}

    //public File projectAnalysisCsv() {
    //    return new File(projectResultsDir(), "projectAnalysis.csv");
    //}
}
