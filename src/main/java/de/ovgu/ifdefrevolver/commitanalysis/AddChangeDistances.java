package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.data.IMinimalSnapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.input.ProjectInformationReader;
import de.ovgu.ifdefrevolver.bugs.correlate.main.ProjectInformationConfig;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.input.RevisionsCsvReader;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDbCsvReader;
import de.ovgu.ifdefrevolver.util.LinkedGroupingListMap;
import de.ovgu.ifdefrevolver.util.ThreadProcessor;
import de.ovgu.skunk.detection.output.CsvFileWriterHelper;
import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.*;

public class AddChangeDistances {
    private static final Logger LOG = Logger.getLogger(AddChangeDistances.class);
    private ListChangedFunctionsConfig config;
    private int errors;
    private CommitsDistanceDb commitsDistanceDb;

    private FunctionMoveResolver moveResolver;

    private AgeRequestStats ageRequestStats = new AgeRequestStats();
    private Map<Date, List<FunctionChangeRow>> changesInSnapshots;
    private ProjectInformationReader<ListChangedFunctionsConfig> projectInfo;
    private Map<Date, List<AllFunctionsRow>> allFunctionsInSnapshots;

    /**
     * Number of commit snapshots per commit window
     */
    private static final int WINDOW_SIZE = 10;

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
        main.execute();
        System.exit(main.errors);
    }

    private static class ProcessingStats {
        final int total;
        int processed;

        public ProcessingStats(int total) {
            this.total = total;
        }

        public synchronized int increaseProcessed() {
            this.processed++;
            return this.processed;
        }
    }

    static class AggregatedFunctionChangeStats {
        final int numCommits;
        final int linesChanged, linesAdded, linesDeleted;

        public AggregatedFunctionChangeStats(int numCommits, int linesAdded, int linesDeleted) {
            this.numCommits = numCommits;
            this.linesAdded = linesAdded;
            this.linesDeleted = linesDeleted;
            this.linesChanged = linesAdded + linesDeleted;
        }

        public static AggregatedFunctionChangeStats fromChanges(Set<FunctionChangeRow> changes) {
            int linesAdded = 0;
            int linesDeleted = 0;
            Set<String> commits = new HashSet<>();
            for (FunctionChangeRow change : changes) {
                commits.add(change.commitId);
                switch (change.modType) {
                    case ADD:
                    case DEL:
                        continue;
                }
                linesAdded += change.linesAdded;
                linesDeleted += change.linesDeleted;
            }

            return new AggregatedFunctionChangeStats(commits.size(), linesAdded, linesDeleted);
        }
    }

    ProcessingStats processingStats;

    private void execute() {
        LOG.debug("Reading information about commit parent-child relationships.");
        this.errors = 0;
        File commitParentsFile = new File(config.projectResultsDir(), "commitParents.csv");
        LOG.debug("Reading information about commit parent-child relationships from " + commitParentsFile);
        CommitsDistanceDbCsvReader distanceReader = new CommitsDistanceDbCsvReader();
        this.commitsDistanceDb = distanceReader.dbFromCsv(commitParentsFile);
        LOG.debug("Preprocessing commit distance information.");
        this.commitsDistanceDb.ensurePreprocessed();
        LOG.debug("Done reading commit parent-child relationships.");

        RevisionsCsvReader revisionsReader = new RevisionsCsvReader(config.revisionCsvFile());
        revisionsReader.readAllCommits();

        projectInfo = new ProjectInformationReader<>(config);
        LOG.debug("Reading project information");
        projectInfo.readSnapshotsAndRevisionsFile();
        LOG.debug("Done reading project information");

        Collection<Date> realSnapshotDates = projectInfo.getSnapshotDatesFiltered(config);
        Optional<Date> leftOverSnapshotDate = config.getDummySnapshotDateToCoverRemainingChanges();
        Collection<Date> allChangesSnapshotDates = new LinkedHashSet<>(realSnapshotDates);
        if (leftOverSnapshotDate.isPresent()) {
            allChangesSnapshotDates.add(leftOverSnapshotDate.get());
        }

        LOG.debug("Reading all function changes.");
        this.changesInSnapshots = readChangesInSnapshots(allChangesSnapshotDates);
        LOG.debug("Reading all function definitions.");

        this.moveResolver = buildMoveResolver(changesInSnapshots);

        final Set<Map.Entry<FunctionId, List<FunctionChangeRow>>> functionChangeEntries =
                moveResolver.getChangesByFunction().entrySet();
        LOG.debug("Done reading all function changes. Number of distinct changed functions: " + functionChangeEntries.size());

        allFunctionsInSnapshots = readAllFunctionsInSnapshots(realSnapshotDates);

        List<CommitWindow> allWindows = groupSnapshots();

        try {
            //processingStats = new ProcessingStats(functionChangeEntries.size());
            //(new ChangeEntryProcessor()).processItems(functionChangeEntries.iterator(), config.getNumThreads());
            processingStats = new ProcessingStats(allWindows.size());
            (new AllFunctionsProcessor()).processItems(allWindows.iterator(), config.getNumThreads());
        } catch (UncaughtWorkerThreadException e) {
            throw new RuntimeException(e);
        }

        LOG.info(ageRequestStats);
    }

    private static class CommitWindow {
        public final Set<AllFunctionsRowInWindow> allFunctions;
        public final Set<String> commits;
        public final Date date;

        public CommitWindow(Date date, Set<String> commits, Set<AllFunctionsRowInWindow> allFunctions) {
            this.allFunctions = allFunctions;
            this.commits = commits;
            this.date = date;
        }
    }

    private List<CommitWindow> groupSnapshots() {
        LinkedGroupingListMap<Integer, Snapshot> snapshotsByBranch = new LinkedGroupingListMap();
        int totalSnapshots = 0;
        for (Snapshot s : projectInfo.getSnapshots().values()) {
            snapshotsByBranch.put(s.getBranch(), s);
            totalSnapshots++;
        }

        LinkedGroupingListMap<Integer, CommitWindow> windowsByBranch = new LinkedGroupingListMap<>();
        final int WINDOW_SIZE = AddChangeDistances.WINDOW_SIZE;
        int totalSnapshotsDiscarded = 0;
        for (Map.Entry<Integer, List<Snapshot>> e : snapshotsByBranch.getMap().entrySet()) {
            final Integer branch = e.getKey();
            List<Snapshot> remainingSnapshots = e.getValue();
            int windowsCreated = 0;
            while (remainingSnapshots.size() >= WINDOW_SIZE) {
                LOG.info("Creating window " + (windowsCreated + 1));
                List<Snapshot> snapshotsInWindow = remainingSnapshots.subList(0, WINDOW_SIZE);
                remainingSnapshots = remainingSnapshots.subList(WINDOW_SIZE, remainingSnapshots.size());
                CommitWindow window = windowFromSnapshots(snapshotsInWindow);
                windowsByBranch.put(branch, window);
                windowsCreated++;
            }
            LOG.info("Created " + windowsCreated + " window(s) for branch " + branch
                    + ". Discarding " + remainingSnapshots.size() + " snapshot(s).");
            totalSnapshotsDiscarded += remainingSnapshots.size();
        }

        List<CommitWindow> result = new ArrayList<>();
        windowsByBranch.getMap().values().forEach((windows) -> windows.forEach((w) -> result.add(w)));
        int discardedPercent = Math.round(totalSnapshotsDiscarded * 100.0f / totalSnapshots);
        LOG.info("Created " + result.size() + " window(s) in total. Discarded " + totalSnapshotsDiscarded
                + " out of " + totalSnapshots + " snapshots(s) (" + discardedPercent + "%).");
        return result;
    }

    private CommitWindow windowFromSnapshots(List<Snapshot> snapshots) {
        Map<FunctionId, AllFunctionsRowInWindow> allFunctionsMap = new LinkedHashMap<>();
        Set<String> commits = new LinkedHashSet<>();
        int iSnapshot = 0;
        final int numSnapshots = snapshots.size();
        for (Snapshot s : snapshots) {
            List<AllFunctionsRow> funcs = allFunctionsInSnapshots.get(s.getSnapshotDate());
            List<Snapshot> snapshotsBefore = snapshots.subList(0, iSnapshot);
            List<Snapshot> snapshotsIncludingAndAfter = snapshots.subList(iSnapshot, numSnapshots);
            for (AllFunctionsRow f : funcs) {
                if (!allFunctionsMap.containsKey(f.functionId)) {
                    AllFunctionsRowInWindow extendedF = new AllFunctionsRowInWindow(f, snapshotsBefore, snapshotsIncludingAndAfter);
                    allFunctionsMap.put(f.functionId, extendedF);
                }
            }
            commits.addAll(s.getCommitHashes());
            iSnapshot++;
        }
        Set<AllFunctionsRowInWindow> allFunctions = new LinkedHashSet<>(allFunctionsMap.values());
        return new CommitWindow(snapshots.get(0).getSnapshotDate(), commits, allFunctions);
    }

    private String getFirstCommitOfSnapshot(Date snapshotDate) {
        Snapshot snapshot = projectInfo.getSnapshots().get(snapshotDate);
        return snapshot.getStartHash();
    }

    private Set<String> getCommitsInSnapshot(Date snapshotDate) {
        Snapshot snapshot = projectInfo.getSnapshots().get(snapshotDate);
        return snapshot.getCommitHashes();
    }

    private class AllFunctionsProcessor extends ThreadProcessor<CommitWindow> {
        @Override
        protected void processItem(CommitWindow window) {
            //final Date snapshotDate = snapshot.getKey();
            CsvFileWriterHelper writerHelper = new CsvFileWriterHelper() {
                @Override
                protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
                    csv.printRecord("FUNCTION_SIGNATURE", "FILE", "AGE", "DISTANCE", "COMMITS", "LINES_ADDED", "LINES_DELETED", "LINES_CHANGED");
                    //List<AllFunctionsRow> allFunctions = snapshot.getValue();
                    //Set<String> commitsInSnapshot = getCommitsInSnapshot(snapshotDate);
                    //String firstCommitOfSnapshot = getFirstCommitOfSnapshot(snapshotDate);

                    for (AllFunctionsRowInWindow function : window.allFunctions) {
                        processFunction(function, window, csv);
                    }
                }
            };

            File resultFile = new File(config.snapshotResultsDirForDate(window.date),
                    "all_functions_with_age_dist_and_changes.csv");
            writerHelper.write(resultFile);

            final int entriesProcessed = processingStats.increaseProcessed();
            int percentage = Math.round(entriesProcessed * 100.0f / processingStats.total);
            LOG.info("Processed window " + entriesProcessed + "/" + processingStats.total + " (" +
                    percentage + "%).");
        }

        private void processFunction(final AllFunctionsRowInWindow function, final CommitWindow window, CSVPrinter csv) {
            final FunctionId functionId = function.functionId;
            final Set<String> allDirectCommitIds = getAllDirectCommitIds(functionId);
            final Set<String> directCommitIdsInSnapshot = new HashSet<>(allDirectCommitIds);
            directCommitIdsInSnapshot.retainAll(window.commits);

            FunctionHistory history = moveResolver.getFunctionHistory(functionId, allDirectCommitIds);
            history.setAgeRequestStats(ageRequestStats);
            if (history.knownAddsForFunction.isEmpty()) {
                ageRequestStats.increaseFunctionsWithoutAnyKnownAddingCommits(functionId);
            }

            FunctionFuture future = moveResolver.getFunctionFuture(functionId, directCommitIdsInSnapshot);
            Set<FunctionChangeRow> changesToFunctionAndAliasesInSnapshot = future.getChangesFilteredByCommitIds(window.commits);
            AggregatedFunctionChangeStats changeStats = AggregatedFunctionChangeStats.fromChanges(changesToFunctionAndAliasesInSnapshot);

            String startHashOfFirstContainingSnapshot = function.getFirstSnapshotCommit();
            AgeAndDistanceStrings distanceStrings = AgeAndDistanceStrings.fromHistoryAndCommit(history, startHashOfFirstContainingSnapshot);

            try {
                csv.printRecord(functionId.signature, functionId.file, distanceStrings.ageString, distanceStrings.distanceString,
                        changeStats.numCommits, changeStats.linesAdded, changeStats.linesDeleted, changeStats.linesChanged);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        private Set<String> getAllDirectCommitIds(FunctionId function) {
            final List<FunctionChangeRow> directChanges = moveResolver.getChangesByFunction().get(function);
            if (directChanges == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(function + " is never changed.");
                }
                return Collections.emptySet();
            }

            final Set<String> allDirectCommitIds = new LinkedHashSet<>(directChanges.size());
            for (FunctionChangeRow r : directChanges) {
                allDirectCommitIds.add(r.commitId);
            }
            return allDirectCommitIds;
        }
    }

    private class ChangeEntryProcessor extends ThreadProcessor<Map.Entry<FunctionId, List<FunctionChangeRow>>> {
        @Override
        public void processItems(Iterator<Map.Entry<FunctionId, List<FunctionChangeRow>>> entryIterator, int numThreads) throws UncaughtWorkerThreadException {
            System.out.println("MOD_TYPE,FUNCTION_SIGNATURE,FILE,COMMIT,AGE,DIST");
            super.processItems(entryIterator, numThreads);
        }

        private class DistanceMemoizer {
            Map<String, AgeAndDistanceStrings> knownDistancesCache = new HashMap<>();
            final FunctionHistory history;

            public DistanceMemoizer(FunctionHistory history) {
                this.history = history;
            }

            public AgeAndDistanceStrings getDistanceStrings(String currentCommit) {
                AgeAndDistanceStrings distanceStrings = knownDistancesCache.get(currentCommit);
                if (distanceStrings == null) {
                    distanceStrings = AgeAndDistanceStrings.fromHistoryAndCommit(history, currentCommit);
                    knownDistancesCache.put(currentCommit, distanceStrings);
                }
                return distanceStrings;
            }
        }

        @Override
        protected void processItem(Map.Entry<FunctionId, List<FunctionChangeRow>> e) {
            final FunctionId function = e.getKey();
            final List<FunctionChangeRow> directChanges = e.getValue();
            final Set<String> directCommitIds = new HashSet<>();
            for (FunctionChangeRow r : directChanges) {
                directCommitIds.add(r.commitId);
            }

            FunctionHistory history = moveResolver.getFunctionHistory(function, directCommitIds);
            history.setAgeRequestStats(ageRequestStats);
            DistanceMemoizer distanceMemoizer = new DistanceMemoizer(history);

            checkForUnknownFunctionAge(function, history);

            for (FunctionChangeRow change : directChanges) {
                final String currentCommit = change.commitId;
                final AgeAndDistanceStrings distanceStrings = distanceMemoizer.getDistanceStrings(currentCommit);

                String line = change.modType + ",\"" + function.signature + "\"," + function.file + "," + currentCommit + "," + distanceStrings.ageString + "," + distanceStrings.distanceString;
                synchronized (System.out) {
                    System.out.println(line);
                }
            }

            logEntriesProcessed();
        }

        private void checkForUnknownFunctionAge(FunctionId function, FunctionHistory history) {
            if (history.knownAddsForFunction.isEmpty()) {
                ageRequestStats.increaseFunctionsWithoutAnyKnownAddingCommits(function);
                LOG.warn("No known creating commits for function '" + function + "'.");
            }
        }

        private void logEntriesProcessed() {
            final int entriesProcessed = processingStats.increaseProcessed();
            int percentage = Math.round(entriesProcessed * 100.0f / processingStats.total);
            LOG.info("Processed entry " + entriesProcessed + "/" + processingStats.total + " (" +
                    percentage + "%).");
        }
    }

    private FunctionMoveResolver buildMoveResolver(Map<Date, List<FunctionChangeRow>> changesInSnapshots) {
        FunctionMoveResolver result = new FunctionMoveResolver(commitsDistanceDb);
        for (List<FunctionChangeRow> changes : changesInSnapshots.values()) {
            for (FunctionChangeRow change : changes) {
                result.putChange(change);
            }
        }
        result.parseRenames();
        return result;
    }


    private static class AgeAndDistanceStrings {
        final String ageString;
        final String distanceString;

        public AgeAndDistanceStrings(String ageString, String distanceString) {
            this.ageString = ageString;
            this.distanceString = distanceString;
        }

        public static AgeAndDistanceStrings fromHistoryAndCommit(FunctionHistory history, String currentCommit) {
            final int minDist = history.getMinDistToPreviousEdit(currentCommit);
            final String minDistStr = minDist < Integer.MAX_VALUE ? Integer.toString(minDist) : "";

            final int age = history.getFunctionAgeAtCommit(currentCommit);
            final String ageStr = age < Integer.MAX_VALUE ? Integer.toString(age) : "";

            return new AgeAndDistanceStrings(ageStr, minDistStr);
        }
    }

    private Map<Date, List<FunctionChangeRow>> readChangesInSnapshots(Collection<Date> snapshotsToProcesses) {
        LOG.debug("Reading function changes for " + snapshotsToProcesses.size() + " snapshot(s).");
        int numChanges = 0;
        FunctionChangeHunksCsvReader reader = new FunctionChangeHunksCsvReader();
        Map<Date, List<FunctionChangeRow>> result = new LinkedHashMap<>();
        for (Date snapshotDate : snapshotsToProcesses) {
            List<FunctionChangeRow> functionChanges = reader.readFile(config, snapshotDate);
            result.put(snapshotDate, functionChanges);
            numChanges += functionChanges.size();
        }
        LOG.debug("Read " + numChanges + " function change(s).");
        return result;
    }

    private Map<Date, List<AllFunctionsRow>> readAllFunctionsInSnapshots(Collection<Date> snapshotsToProcesses) {
        LOG.debug("Reading functions defined in " + snapshotsToProcesses.size() + " snapshot(s).");
        int numFunctionDefinitions = 0;
        AllFunctionsCsvReader reader = new AllFunctionsCsvReader();
        Map<Date, List<AllFunctionsRow>> result = new LinkedHashMap<>();
        for (Date snapshotDate : snapshotsToProcesses) {
            List<AllFunctionsRow> functions = reader.readFile(config, snapshotDate);
            result.put(snapshotDate, functions);
            rememberFunctionsKnownToExistAt(snapshotDate, functions);
            numFunctionDefinitions += functions.size();
        }
        LOG.debug("Read " + numFunctionDefinitions + " function definitions(s).");
        return result;
    }

    private void rememberFunctionsKnownToExistAt(Date snapshotDate, List<AllFunctionsRow> functions) {
        SortedMap<Date, Snapshot> snapshots = projectInfo.getSnapshots();
        Snapshot snapshot = snapshots.get(snapshotDate);
        String startHash = snapshot.getStartHash();
        for (AllFunctionsRow row : functions) {
            moveResolver.putFunctionKnownToExistAt(row.functionId, startHash);
//            if (startHash.equalsIgnoreCase("0098ed12c242cabb34646a4453f2c1b012c919c7")) {
//                LOG.warn("XXX Exists at: " + startHash + "," + row.functionId);
//            }
        }
    }

    private void logSnapshotsToProcess(Collection<IMinimalSnapshot> snapshotsToProcesses) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("The following snapshots will be processed:");
            for (IMinimalSnapshot s : snapshotsToProcesses) {
                LOG.debug("" + s);
            }
        }
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
            if (dummyLine.hasOption(ListChangedFunctionsConfig.OPT_HELP)) {
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
        this.config = new ListChangedFunctionsConfig();

        ProjectInformationConfig.parseProjectNameFromCommandLine(line, this.config);
        ProjectInformationConfig.parseProjectResultsDirFromCommandLine(line, this.config);
        ProjectInformationConfig.parseSnapshotsDirFromCommandLine(line, this.config);

        if (line.hasOption(ListChangedFunctionsConfig.OPT_REPO)) {
            config.setRepoDir(line.getOptionValue(ListChangedFunctionsConfig.OPT_REPO));
        } else {
            config.setRepoDir(Paths.get(ListChangedFunctionsConfig.DEFAULT_REPOS_DIR_NAME, this.config.getProject(), ".git").toString());
        }
        config.validateRepoDir();

        if (line.hasOption(ListChangedFunctionsConfig.OPT_THREADS)) {
            String threadsString = line.getOptionValue(ListChangedFunctionsConfig.OPT_THREADS);
            int numThreads;
            try {
                numThreads = Integer.valueOf(threadsString);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid value for option `-" + ListChangedFunctionsConfig.OPT_THREADS
                        + "': Not a valid integer: " + threadsString);
            }
            if (numThreads < 1) {
                throw new RuntimeException("Invalid value for option `-" + ListChangedFunctionsConfig.OPT_THREADS
                        + "': Number of threads must be an integer >= 1.");
            }
            config.setNumThreads(numThreads);
        }

        List<String> snapshotDateNames = line.getArgList();
        if (!snapshotDateNames.isEmpty()) {
            ListChangedFunctionsConfig.parseSnapshotFilterDates(snapshotDateNames, config);
        }
    }

    private Options makeOptions(boolean forHelp) {
        boolean required = !forHelp;
        Options options = new Options();
        // @formatter:off

        // --help= option
        options.addOption(ProjectInformationConfig.helpCommandLineOption());

        // Options for describing project locations
        options.addOption(ProjectInformationConfig.projectNameCommandLineOption(required));
        options.addOption(ProjectInformationConfig.resultsDirCommandLineOption());
        options.addOption(ProjectInformationConfig.snapshotsDirCommandLineOption());

        // --repo=foo/bar/.git GIT repository location
        options.addOption(Option.builder(String.valueOf(ListChangedFunctionsConfig.OPT_REPO))
                .longOpt(ListChangedFunctionsConfig.OPT_REPO_L)
                .desc("Directory containing the git repository to analyze." + " [Default="
                        + ListChangedFunctionsConfig.DEFAULT_REPOS_DIR_NAME + "/<project>/.git]")
                .hasArg().argName("DIR")
                //.required(required)
                .build());

        // --threads=1 options
        options.addOption(Option.builder(String.valueOf(ListChangedFunctionsConfig.OPT_THREADS))
                .longOpt(ListChangedFunctionsConfig.OPT_THREADS_L)
                .desc("Number of parallel analysis threads. Must be at least 1." + " [Default="
                        + ListChangedFunctionsConfig.DEFAULT_NUM_THREADS + "]")
                .hasArg().argName("NUM")
                .type(Integer.class)
                .build());

        // @formatter:on
        return options;
    }

    private String progName() {
        return this.getClass().getSimpleName();
    }
}
