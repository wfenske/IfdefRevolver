package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.data.IMinimalSnapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.input.ProjectInformationReader;
import de.ovgu.ifdefrevolver.bugs.correlate.main.ProjectInformationConfig;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.input.RevisionsCsvReader;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDbCsvReader;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;

import java.io.File;
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

    private static class AgeRequestStats {
        int ageRequests = 0, actualAge = 0, guessed0Age = 0, guessedOtherAge = 0, noAgeAtAll = 0;
        int functionsWithoutAnyKnownAddingCommits = 0;
    }

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

        ProjectInformationReader<ListChangedFunctionsConfig> projectInfo = new ProjectInformationReader<>(config);
        LOG.debug("Reading project information");
        projectInfo.readSnapshotsAndRevisionsFile();
        LOG.debug("Done reading project information");

        Collection<Date> snapshotDates = getDatesOfActualSnapshots(projectInfo);
        maybeAddDummySnapshotToCoverRemainingChanges(snapshotDates);

        moveResolver = new FunctionMoveResolver(commitsDistanceDb);

        LOG.debug("Reading all function changes.");
        readFunctionsChangedInSnapshots(snapshotDates);
        final Set<Map.Entry<FunctionId, List<FunctionChangeRow>>> functionChangeEntries =
                moveResolver.getChangesByFunction().entrySet();
        final int totalEntries = functionChangeEntries.size();
        LOG.debug("Done reading all function changes. Number of distinct changed functions: " + totalEntries);

        moveResolver.parseRenames();

        System.out.println("MOD_TYPE,FUNCTION_SIGNATURE,FILE,COMMIT,AGE,DIST");
        int entriesProcessed = 0;
        for (Map.Entry<FunctionId, List<FunctionChangeRow>> e : functionChangeEntries) {
            final FunctionId function = e.getKey();
            final List<FunctionChangeRow> directChanges = e.getValue();
            final Set<String> directCommitIds = new HashSet<>();
            for (FunctionChangeRow r : directChanges) {
                directCommitIds.add(r.commitId);
            }
//            final Set<FunctionId> functionAliases = moveResolver.getFunctionAliases(function, directCommitIds);
//
//            // All the commits that have created this function or a previous version of it.
//            Set<String> knownAddsForFunction = moveResolver.getAddingCommitsIncludingAliases(functionAliases);
//
//            final Set<String> nonDeletingCommitsToFunctionAndAliases = moveResolver.getNonDeletingCommitsToFunctionIncludingAliases(functionAliases);
//            final Set<String> guessedAddsForFunction = commitsDistanceDb.filterAncestorCommits(nonDeletingCommitsToFunctionAndAliases);
//            guessedAddsForFunction.removeAll(knownAddsForFunction);

            FunctionHistory history = moveResolver.getFunctionHistory(function, directCommitIds);

            if (history.knownAddsForFunction.isEmpty()) {
                ageRequestStats.functionsWithoutAnyKnownAddingCommits++;
                LOG.warn("No known creating commits for function '" + function + "'.");
            }

//            if (!guessedAddsForFunction.isEmpty()) {
//                LOG.warn("Some unknown creating commits for function '" + function +
//                        "'. Assuming the following additional creating commits: " + guessedAddsForFunction);
//            }

            Map<String, AgeAndDistanceStrings> knownDistancesCache = new HashMap<>();

            for (FunctionChangeRow change : directChanges) {
                final String currentCommit = change.commitId;
                AgeAndDistanceStrings distanceStrings = knownDistancesCache.get(currentCommit);
                if (distanceStrings == null) {
                    distanceStrings = getAgeAndDistanceStrings(history, currentCommit);
                    knownDistancesCache.put(currentCommit, distanceStrings);
                }

                System.out.println(change.modType + ",\"" + function.signature + "\"," + function.file + "," + currentCommit + "," + distanceStrings.ageString + "," + distanceStrings.distanceString);
            }

            entriesProcessed++;
            int percentage = Math.round(entriesProcessed * 100.0f / totalEntries);
            LOG.info("Processed entry " + entriesProcessed + "/" + totalEntries + " (" +
                    percentage + "%).");
        }
        LOG.debug("Functions without any known addition: " + ageRequestStats.functionsWithoutAnyKnownAddingCommits);
        LOG.debug("Found ages: ageRequests: " + ageRequestStats.ageRequests + " actualAge: " + ageRequestStats.actualAge + " errors={guessed0Age: " + ageRequestStats.guessed0Age + " guessedOtherAge: " + ageRequestStats.guessedOtherAge +
                " noAgeAtAll: " + ageRequestStats.noAgeAtAll + "}");
    }

    private AgeAndDistanceStrings getAgeAndDistanceStrings(FunctionHistory history, String currentCommit) {
        AgeAndDistanceStrings distanceStrings;
        final int minDist = getMinDistToPreviousCommit(history, currentCommit);
        final String minDistStr = minDist < Integer.MAX_VALUE ? Integer.toString(minDist) : "";

        final int age = getFunctionAgeAtCommit(history, currentCommit);
        final String ageStr = age < Integer.MAX_VALUE ? Integer.toString(age) : "";

        distanceStrings = new AgeAndDistanceStrings(ageStr, minDistStr);
        return distanceStrings;
    }

    private int getFunctionAgeAtCommit(FunctionHistory history, final String currentCommit) {
        ageRequestStats.ageRequests++;

        if (history.knownAddsForFunction.contains(currentCommit)) {
            ageRequestStats.actualAge++;
            return 0;
        }

        int currentMinAgeAmongKnownAdds = Integer.MAX_VALUE;
        for (String addingCommit : history.knownAddsForFunction) {
            if (currentCommit.equals(addingCommit)) continue;
            Optional<Integer> age = commitsDistanceDb.minDistance(currentCommit, addingCommit);
            if (age.isPresent()) {
                currentMinAgeAmongKnownAdds = Math.min(age.get(), currentMinAgeAmongKnownAdds);
            }
        }

        if (currentMinAgeAmongKnownAdds < Integer.MAX_VALUE) {
            ageRequestStats.actualAge++;
            return currentMinAgeAmongKnownAdds;
        }

        LOG.warn("Forced to assume alternative adding commit. Function: " + history.function + " Current commit: " + currentCommit);
        if (history.guessedAddsForFunction.contains(currentCommit)) {
            ageRequestStats.guessed0Age++;
            return 0;
        }

        int currentMinAgeAmongGuessedAdds = Integer.MAX_VALUE;
        int currentMinAgeAmongGuessedAddsIncluding0 = Integer.MAX_VALUE;

        for (String addingCommit : history.guessedAddsForFunction) {
            if (currentCommit.equals(addingCommit)) continue;
            Optional<Integer> age = commitsDistanceDb.minDistance(currentCommit, addingCommit);
            if (!age.isPresent()) continue;
            int ageValue = age.get();
            if (ageValue == 0) {
                LOG.warn("Edit distance between commits is 0? " + currentCommit + " .. " + addingCommit);
                currentMinAgeAmongGuessedAddsIncluding0 = Math.min(currentMinAgeAmongGuessedAddsIncluding0, ageValue);
            } else {
                currentMinAgeAmongGuessedAdds = Math.min(currentMinAgeAmongGuessedAdds, ageValue);
            }
        }

        if (currentMinAgeAmongGuessedAdds < Integer.MAX_VALUE) {
            ageRequestStats.guessedOtherAge++;
            return currentMinAgeAmongGuessedAdds;
        }

        if (currentMinAgeAmongGuessedAddsIncluding0 < Integer.MAX_VALUE) {
            ageRequestStats.guessed0Age++;
            return currentMinAgeAmongGuessedAddsIncluding0;
        }

        ageRequestStats.noAgeAtAll++;
        return Integer.MAX_VALUE;
    }

    private int getMinDistToPreviousCommit(FunctionHistory history, String currentCommit) {
        if (history.knownAddsForFunction.contains(currentCommit)) {
            return 0;
        }

        if (history.guessedAddsForFunction.contains(currentCommit)) {
            LOG.warn("Forced to assume 0 edit distance because the commit has no known ancestors among the"
                    + " function's edits. Function: " + history.function + " Current commit: " + currentCommit);
            return 0;
        }

        int minDist = Integer.MAX_VALUE;
        int minDistIncluding0 = Integer.MAX_VALUE;

        for (String otherCommit : history.nonDeletingCommitsToFunctionAndAliases) {
            if (currentCommit.equals(otherCommit)) continue;
            Optional<Integer> currentDist = commitsDistanceDb.minDistance(currentCommit, otherCommit);
            if (!currentDist.isPresent()) continue;
            final int currentDistValue = currentDist.get();
            if (currentDistValue == 0) {
                LOG.warn("Distance between commits is 0? " + currentCommit + " .. " + otherCommit);
                minDistIncluding0 = Math.min(minDistIncluding0, currentDistValue);
            } else {
                minDist = Math.min(minDist, currentDistValue);
            }
        }

        final int result = (minDist < Integer.MAX_VALUE) ? minDist : minDistIncluding0;

        if (result == 0) {
            LOG.warn("Distance to most recent commits is 0, although it is not an adding commit. Function: "
                    + history.function + " Commit: " + currentCommit);
        }

        return result;
    }

    private static class AgeAndDistanceStrings {
        final String ageString;
        final String distanceString;

        private AgeAndDistanceStrings(String ageString, String distanceString) {
            this.ageString = ageString;
            this.distanceString = distanceString;
        }
    }

    private Collection<Date> getDatesOfActualSnapshots(ProjectInformationReader<ListChangedFunctionsConfig> projectInfo) {
        Collection<? extends IMinimalSnapshot> snapshotsToProcesses = projectInfo.getSnapshotsFiltered(config);
        Collection<Date> snapshotDates = new LinkedHashSet<>();
        for (IMinimalSnapshot s : snapshotsToProcesses) {
            snapshotDates.add(s.getSnapshotDate());
        }
        return snapshotDates;
    }

    private void maybeAddDummySnapshotToCoverRemainingChanges(Collection<Date> snapshotDates) {
        if (!config.getSnapshotFilter().isPresent()) {
            Date dummySnapshotDate = new Date(0);
            File dummySnapshotFile = new File(config.snapshotResultsDirForDate(dummySnapshotDate),
                    FunctionChangeHunksColumns.FILE_BASENAME);
            if (dummySnapshotFile.exists()) {
                LOG.info("Adding changes from dummy snapshot in " + dummySnapshotFile);
                snapshotDates.add(dummySnapshotDate);
            }
        }
    }

    private void readFunctionsChangedInSnapshots(Collection<Date> snapshotsToProcesses) {
        LOG.debug("Reading function changes for " + snapshotsToProcesses.size() + " snapshot(s).");
        int numChanges = 0;
        FunctionChangeHunksCsvReader reader = new FunctionChangeHunksCsvReader();
        for (Date snapshotDate : snapshotsToProcesses) {
            Collection<FunctionChangeRow> functionChanges = reader.readFile(config, snapshotDate);
            for (FunctionChangeRow change : functionChanges) {
                moveResolver.putChange(change);
                numChanges++;
            }
        }
        LOG.debug("Read " + numChanges + " function change(s).");
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
