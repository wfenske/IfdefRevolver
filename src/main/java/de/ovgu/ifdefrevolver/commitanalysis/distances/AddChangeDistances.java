package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.input.ProjectInformationReader;
import de.ovgu.ifdefrevolver.bugs.correlate.main.ProjectInformationConfig;
import de.ovgu.ifdefrevolver.bugs.correlate.output.JointDataColumns;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.input.RevisionsCsvReader;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDbCsvReader;
import de.ovgu.ifdefrevolver.commitanalysis.*;
import de.ovgu.ifdefrevolver.commitanalysis.branchtraversal.FunctionGenealogy;
import de.ovgu.ifdefrevolver.commitanalysis.branchtraversal.GenealogyTracker;
import de.ovgu.skunk.detection.output.CsvEnumUtils;
import de.ovgu.skunk.detection.output.CsvFileWriterHelper;
import de.ovgu.skunk.detection.output.CsvRowProvider;
import de.ovgu.skunk.util.LinkedGroupingListMap;
import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class AddChangeDistances {
    private static final Logger LOG = Logger.getLogger(AddChangeDistances.class);
    private AddChangeDistancesConfig config;
    private int errors;
    private CommitsDistanceDb commitsDistanceDb;

    private Map<Date, List<FunctionChangeRow>> changesInSnapshots;
    private ProjectInformationReader<AddChangeDistancesConfig> projectInfo;
    private Map<Date, List<AllFunctionsRow>> allFunctionsInSnapshots;
    private Map<Date, List<AbResRow>> annotationDataInSnapshots;

    private RevisionsCsvReader revisionsReader;
    private FunctionIdFactory functionIdFactory;

    public static void main(String[] args) {
        AddChangeDistances main = new AddChangeDistances();
        try {
            main.parseCommandLineArgs(args);
        } catch (Exception e) {
            System.err.println("Error while processing command line arguments: " + e);
            e.printStackTrace();
            System.err.flush();
            System.out.flush();
            System.exit(1);
        }

//        byte b = 1;
//        System.out.println(b << 0);
//        System.out.println(b << 1);
//        System.out.println(b << 2);
//        System.out.println(b << 3);
//
//        System.out.println(b << 4);
//        System.out.println(b << 5);
//        System.out.println((byte) (b << 6));
//        System.out.println((byte) (b << 7));
//
//        System.exit(0);

        main.execute();
        System.exit(main.errors);
    }

    private void execute() {
        LOG.debug("Reading information about commit parent-child relationships.");
        this.errors = 0;
        File commitParentsFile = new File(config.projectResultsDir(), "commitParents.csv");
        LOG.debug("Reading information about commit parent-child relationships from " + commitParentsFile);
        CommitsDistanceDbCsvReader distanceReader = new CommitsDistanceDbCsvReader();
        this.commitsDistanceDb = distanceReader.dbFromCsv(config);
        LOG.debug("Preprocessing commit distance information.");
        this.commitsDistanceDb.ensurePreprocessed();
        LOG.debug("Done reading commit parent-child relationships.");

        this.revisionsReader = new RevisionsCsvReader(commitsDistanceDb, config.revisionCsvFile());
        revisionsReader.readCommitsThatModifyCFiles();

        projectInfo = new ProjectInformationReader<>(config, commitsDistanceDb);
        LOG.debug("Reading project information");
        projectInfo.readSnapshotsAndRevisionsFile();
        LOG.debug("Done reading project information");

        initializeDistanceInformation();

        Collection<Date> realSnapshotDates = projectInfo.getSnapshotDatesFiltered(config);
        Optional<Date> leftOverSnapshotDate = config.getDummySnapshotDateToCoverRemainingChanges();
        Collection<Date> allChangesSnapshotDates = new LinkedHashSet<>(realSnapshotDates);
        if (leftOverSnapshotDate.isPresent()) {
            allChangesSnapshotDates.add(leftOverSnapshotDate.get());
        }

        this.functionIdFactory = new FunctionIdFactory();

        LOG.debug("Reading all function definitions, changes, and annotation data.");
        changesInSnapshots = readChangesInSnapshots(allChangesSnapshotDates);
        allFunctionsInSnapshots = readAllFunctionsInSnapshots(realSnapshotDates);
        annotationDataInSnapshots = readAllAbResInSnapshots(realSnapshotDates);

        this.functionIdFactory = null;

        LOG.debug("Tracking genealogies.");
        trackGenealogies();
    }

    private void initializeDistanceInformation() {
        final List<Commit> commitsInTraversalOrder = this.projectInfo.getAllSnapshots().stream()
                .flatMap(s -> s.getCommits().stream())
                .collect(Collectors.toList());
        this.commitsDistanceDb.initializeDistanceInformation(commitsInTraversalOrder,
                this.projectInfo.getCommitsThatModifyCFiles());
    }

    private void trackGenealogies() {
        List<FunctionChangeRow>[] changesByCommitKey = groupChangesByCommitKey();
        GenealogyTracker gt = new GenealogyTracker(projectInfo, config, changesByCommitKey,
                allFunctionsInSnapshots, annotationDataInSnapshots);
        final LinkedGroupingListMap<Snapshot, FunctionGenealogy> functionGenealogiesBySnapshotAtWhoseStartTheyArePresent = gt.processCommits();
        final LinkedGroupingListMap<Snapshot, FunctionGenealogy> functionGenealogiesBySnapshotInWhichTheyAreChanged = groupFunctionGenealogiesBySnapshotInWhichTheyAreChanged(functionGenealogiesBySnapshotAtWhoseStartTheyArePresent);

        writeAbSmellAgeSnapshotCsv(functionGenealogiesBySnapshotAtWhoseStartTheyArePresent, functionGenealogiesBySnapshotInWhichTheyAreChanged);
    }

    private LinkedGroupingListMap<Snapshot, FunctionGenealogy> groupFunctionGenealogiesBySnapshotInWhichTheyAreChanged(LinkedGroupingListMap<Snapshot, FunctionGenealogy> functionGenealogiesBySnapshot) {
        Set<FunctionGenealogy> allFunctionGenalogies = new HashSet<>();
        for (List<FunctionGenealogy> gs : functionGenealogiesBySnapshot.getMap().values()) {
            allFunctionGenalogies.addAll(gs);
        }

        LinkedGroupingListMap<Snapshot, FunctionGenealogy> result = new LinkedGroupingListMap<>();
        for (FunctionGenealogy f : allFunctionGenalogies) {
            for (Snapshot s : functionGenealogiesBySnapshot.getMap().keySet()) {
                if (f.isChangedInSnapshot(s)) {
                    result.put(s, f);
                }
            }
        }

        return result;
    }

    private List<FunctionChangeRow>[] groupChangesByCommitKey() {
        final int numCommits = commitsDistanceDb.getNumCommits();
        List<FunctionChangeRow>[] changesByCommitKey = new List[numCommits];
        for (List<FunctionChangeRow> rows : changesInSnapshots.values()) {
            for (FunctionChangeRow row : rows) {
                final int key = row.commit.key;
                List<FunctionChangeRow> changesForKey = changesByCommitKey[key];
                if (changesForKey == null) {
                    changesForKey = new ArrayList<>();
                    changesByCommitKey[key] = changesForKey;
                }
                changesForKey.add(row);
            }
        }

        final List<FunctionChangeRow> NO_COMMITS = Collections.emptyList();
        for (int i = 0; i < numCommits; i++) {
            if (changesByCommitKey[i] == null) {
                changesByCommitKey[i] = NO_COMMITS;
            } else {
                Collections.sort(changesByCommitKey[i], FunctionChangeRow.BY_HUNK_AND_MOD_TYPE);
            }
        }
        return changesByCommitKey;
    }

    private void writeAbSmellAgeSnapshotCsv(LinkedGroupingListMap<Snapshot, FunctionGenealogy> functionGenealogiesBySnapshotAtWhoseStartTheyArePresent, LinkedGroupingListMap<Snapshot, FunctionGenealogy> functionGenealogiesBySnapshotInWhichTheyAreChanged) {
        final int WINDOW_SIZE = config.getWindowSize();
        final int SLIDE = config.getWindowSlide();

        List<Snapshot> allSnapshots = this.projectInfo.getAllSnapshots();
        final int numWindows = Math.max(allSnapshots.size() - WINDOW_SIZE, 0) / SLIDE + 1;

        List<CommitWindow> windows = new ArrayList<>();
        final int lastStartIndex = allSnapshots.size() - WINDOW_SIZE;

        for (int startIndex = 0; startIndex <= lastStartIndex; startIndex += SLIDE) {
            LOG.info("Computing window " + windows.size() + "/" + numWindows);
            final List<Snapshot> snapshotsInWindow = allSnapshots.subList(startIndex, startIndex + WINDOW_SIZE);
            final Snapshot firstSnapshot = allSnapshots.get(startIndex);
            final List<FunctionGenealogy> functionsPresentAtSnapshotStart = functionGenealogiesBySnapshotAtWhoseStartTheyArePresent.get(firstSnapshot);
            final List<FunctionGenealogy> functionsChangedInFirstSnapshotButNotPresentAtItsBeginning = getFunctionsChangedInFirstSnapshotButNotPresentAtItsBeginning(functionGenealogiesBySnapshotInWhichTheyAreChanged, functionsPresentAtSnapshotStart, firstSnapshot);
            CommitWindow window = new CommitWindow(snapshotsInWindow, functionsPresentAtSnapshotStart, functionsChangedInFirstSnapshotButNotPresentAtItsBeginning);
            windows.add(window);
        }
        LOG.info("Done computing windows. " + windows.size() + " windows have been created.");

        writeAbSmellAgeSnapshotCsv(windows);

        LOG.info("Successfully output aggregated change data for all " + windows.size() + " windows.");
    }

    private List<FunctionGenealogy> getFunctionsChangedInFirstSnapshotButNotPresentAtItsBeginning(LinkedGroupingListMap<Snapshot, FunctionGenealogy> functionGenealogiesBySnapshotInWhichTheyAreChanged, List<FunctionGenealogy> functionsPresentAtSnapshotStart, Snapshot firstSnapshot) {
        List<FunctionGenealogy> changedInFirstSnapshot = functionGenealogiesBySnapshotInWhichTheyAreChanged.get(firstSnapshot);
        if (changedInFirstSnapshot == null || changedInFirstSnapshot.isEmpty()) return Collections.emptyList();
        Set<FunctionGenealogy> result = new LinkedHashSet<>(changedInFirstSnapshot);
        result.removeAll(functionsPresentAtSnapshotStart);
        return new ArrayList<>(result);
    }

    private void writeAbSmellAgeSnapshotCsv(List<CommitWindow> windows) {
        CsvFileWriterHelper writerHelper = new CsvFileWriterHelper() {
            @Override
            protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
                final Object[] headerRow = CsvEnumUtils.headerRow(JointDataColumns.class);
                csv.printRecord(headerRow);
                for (CommitWindow window : windows) {
                    CsvRowProvider<FunctionGenealogy, CommitWindow, JointDataColumns> rowProvider = new CsvRowProvider<>(JointDataColumns.class, window);
                    for (FunctionGenealogy functionGenealogy : window.functionsPresentAtSnapshotStart) {
                        Object[] row = rowProvider.dataRow(functionGenealogy);
                        csv.printRecord(row);
                    }

                    for (FunctionGenealogy functionGenealogy : window.functionsChangedInFirstSnapshotButNotPresentAtItsBeginning) {
                        Object[] row = rowProvider.dataRow(functionGenealogy);
                        csv.printRecord(row);
                    }
                }
            }
        };

        File resultFile = new File(config.projectResultsDir(),
                JointDataColumns.FILE_BASENAME);
        writerHelper.write(resultFile);
    }

    private Map<Date, List<FunctionChangeRow>> readChangesInSnapshots(Collection<Date> snapshotsToProcesses) {
        LOG.debug("Reading function changes for " + snapshotsToProcesses.size() + " snapshot(s).");
        int numChanges = 0;
        FunctionChangeHunksCsvReader reader = new FunctionChangeHunksCsvReader(functionIdFactory, commitsDistanceDb);
        Map<Date, List<FunctionChangeRow>> result = new LinkedHashMap<>();
        for (Date snapshotDate : snapshotsToProcesses) {
            List<FunctionChangeRow> functionChanges = reader.readFile(config, snapshotDate);
            result.put(snapshotDate, functionChanges);
            numChanges += functionChanges.size();
        }
        LOG.info("Read " + numChanges + " function change(s).");
        return result;
    }

    private Map<Date, List<AllFunctionsRow>> readAllFunctionsInSnapshots(Collection<Date> snapshotsToProcesses) {
        LOG.debug("Reading functions defined in " + snapshotsToProcesses.size() + " snapshot(s).");
        int numFunctionDefinitions = 0;
        AllFunctionsCsvReader reader = new AllFunctionsCsvReader(functionIdFactory);
        Map<Date, List<AllFunctionsRow>> result = new LinkedHashMap<>();
        for (Date snapshotDate : snapshotsToProcesses) {
            List<AllFunctionsRow> functions = reader.readFile(config, snapshotDate);
            result.put(snapshotDate, functions);
            numFunctionDefinitions += functions.size();
        }
        LOG.info("Read " + numFunctionDefinitions + " function definitions(s).");
        return result;
    }

    private Map<Date, List<AbResRow>> readAllAbResInSnapshots(Collection<Date> snapshotsToProcesses) {
        LOG.debug("Reading annotation data of functions defined in " + snapshotsToProcesses.size() + " snapshot(s).");
        int numFunctionDefinitions = 0;
        AbResCsvReader reader = new AbResCsvReader(functionIdFactory);
        Map<Date, List<AbResRow>> result = new LinkedHashMap<>();
        for (Date snapshotDate : snapshotsToProcesses) {
            List<AbResRow> functions = reader.readFile(config, snapshotDate);
            result.put(snapshotDate, functions);
            numFunctionDefinitions += functions.size();
        }
        LOG.info("Read annotation data for " + numFunctionDefinitions + " function(s).");
        return result;
    }

    /**
     * Analyze command line to decide what to do during runtime
     *
     * @param args the command line arguments
     */
    private void parseCommandLineArgs(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options fakeOptionsForHelp = makeOptions(true);
        Options actualOptions = makeOptions(false);
        CommandLine line;
        try {
            CommandLine dummyLine = parser.parse(fakeOptionsForHelp, args);
            if (dummyLine.hasOption(AddChangeDistancesConfig.OPT_HELP)) {
                System.out.flush();
                System.err.flush();
                HelpFormatter formatter = new HelpFormatter();
                //@formatter:off
                formatter.printHelp(progName()
                                + " [OPTION]... [SNAPSHOT]..."
                        , "Augment each commit that changes a function with the minimum distance" +
                                " (in terms of commits) to the commit that changed the function before." +
                                " By default, all snapshots of and an IfdefRevolver project will be" +
                                " analyzed.  If you wish to analyze only specific snapshots, you can list their dates" +
                                " in YYYY-MM-DD format after the last named command line option." /* header */
                        , actualOptions
                        , null /* footer */
                );
                //@formatter:on
                System.out.flush();
                System.err.flush();
                System.exit(0);
                // We never actually get here due to the preceding
                // System.exit(int) call.
                return;
            }
            line = parser.parse(actualOptions, args);
        } catch (ParseException e) {
            System.out.flush();
            System.err.flush();
            System.err.println("Error in command line: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printUsage(new PrintWriter(System.err, true), 80, progName(), actualOptions);
            System.out.flush();
            System.err.flush();
            System.exit(1);
            // We never actually get here due to the preceding System.exit(int)
            // call.
            return;
        }
        this.config = new AddChangeDistancesConfig();

        ProjectInformationConfig.parseProjectNameFromCommandLine(line, this.config);
//        ProjectInformationConfig.parseProjectResultsDirFromCommandLine(line, this.config);

        if (line.hasOption(ListChangedFunctionsConfig.OPT_REPO)) {
            config.setRepoDir(line.getOptionValue(ListChangedFunctionsConfig.OPT_REPO));
        } else {
            config.setRepoDir(Paths.get(ListChangedFunctionsConfig.DEFAULT_REPOS_DIR_NAME, this.config.getProject()).toString());
        }
        config.validateRepoDir();

        AddChangeDistancesConfig.parseWindowSizeFromCommandLine(line, config);
        AddChangeDistancesConfig.parseWindowSlideFromCommandLine(line, config);
        AddChangeDistancesConfig.parseValidateAfterMergeFromCommandLine(line, config);

//        if (line.hasOption(AddChangeDistancesConfig.OPT_THREADS)) {
//            String threadsString = line.getOptionValue(ListChangedFunctionsConfig.OPT_THREADS);
//            int numThreads;
//            try {
//                numThreads = Integer.valueOf(threadsString);
//            } catch (NumberFormatException e) {
//                throw new RuntimeException("Invalid value for option `-" + ListChangedFunctionsConfig.OPT_THREADS
//                        + "': Not a valid integer: " + threadsString);
//            }
//            if (numThreads < 1) {
//                throw new RuntimeException("Invalid value for option `-" + ListChangedFunctionsConfig.OPT_THREADS
//                        + "': Number of threads must be an integer >= 1.");
//            }
//            config.setNumThreads(numThreads);
//        }

//        List<String> snapshotDateNames = line.getArgList();
//        if (!snapshotDateNames.isEmpty()) {
//            ListChangedFunctionsConfig.parseSnapshotFilterDates(snapshotDateNames, config);
//        }
    }

    private Options makeOptions(boolean forHelp) {
        boolean required = !forHelp;
        Options options = new Options();
        // @formatter:off

        // --help= option
        options.addOption(ProjectInformationConfig.helpCommandLineOption());

        // Options for describing project locations
        options.addOption(ProjectInformationConfig.projectNameCommandLineOption(required));
//        options.addOption(ProjectInformationConfig.resultsDirCommandLineOption());

        // --repo=foo/bar/.git GIT repository location
        options.addOption(Option.builder(String.valueOf(ListChangedFunctionsConfig.OPT_REPO))
                .longOpt(ListChangedFunctionsConfig.OPT_REPO_L)
                .desc("Directory containing the git repository to analyze." + " [Default="
                        + ListChangedFunctionsConfig.DEFAULT_REPOS_DIR_NAME + "/<project>/.git]")
                .hasArg().argName("DIR")
                //.required(required)
                .build());

        options.addOption(Option.builder(
                String.valueOf(AddChangeDistancesConfig.OPT_COMMIT_WINDOW_SIZE))
                .longOpt(AddChangeDistancesConfig.OPT_COMMIT_WINDOW_SIZE_L)
                .desc("Number of snapshots in a commit window, specified as a positive integer. [Default=" + AddChangeDistancesConfig.DEFAULT_WINDOW_SIZE + "]")
                .hasArg().argName("NUM").build());

        options.addOption(Option.builder(
                String.valueOf(AddChangeDistancesConfig.OPT_COMMIT_WINDOW_SLIDE))
                .longOpt(AddChangeDistancesConfig.OPT_COMMIT_WINDOW_SLIDE_L)
                .desc("Number of snapshots to slide over when creating the next commit window, specified as a positive integer. [Default=" + AddChangeDistancesConfig.DEFAULT_WINDOW_SLIDE + "]")
                .hasArg().argName("NUM").build());

        options.addOption(Option.builder()
                .longOpt(AddChangeDistancesConfig.OPT_VALIDATE_AFTER_MERGE_L)
                .desc("Validate computed against actual functions after each merge. [Default=" + AddChangeDistancesConfig.DEFAULT_VALIDATE_AFTER_MERGE + "]")
                .build());

        // @formatter:on
        return options;
    }

    private String progName() {
        return this.getClass().getSimpleName();
    }
}
