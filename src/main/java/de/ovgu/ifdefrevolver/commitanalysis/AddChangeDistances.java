package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.data.IMinimalSnapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.input.ProjectInformationReader;
import de.ovgu.ifdefrevolver.bugs.correlate.main.ProjectInformationConfig;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.input.RevisionsCsvReader;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDbCsvReader;
import de.ovgu.ifdefrevolver.util.SimpleCsvFileReader;
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
    //private Map<FunctionId, FunctionChangeRow> adds;
    //private Map<FunctionId, FunctionChangeRow> dels;
    private GroupingListMap<String, FunctionChangeRow> changesByCommit;
    private GroupingListMap<FunctionId, FunctionChangeRow> changesByFunction;
    /**
     * Key is the new function id (after the rename/move/signature change). Values are the old function ids (before the MOVE event).
     */
    private GroupingHashSetMap<FunctionId, FunctionChangeRow> movesByNewFunctionId = new GroupingHashSetMap<>();

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

    public abstract static class GroupingMap<K, V, C extends Collection<V>> {
        protected final Map<K, C> map;

        public GroupingMap() {
            this.map = newMap();
        }

        protected abstract C newCollection();

        protected Map<K, C> newMap() {
            return new HashMap<>();
        }

        public void put(K key, V value) {
            C valuesForKey = map.get(key);
            if (valuesForKey == null) {
                valuesForKey = newCollection();
                map.put(key, valuesForKey);
            }
            valuesForKey.add(value);
        }

        public C get(K key) {
            return map.get(key);
        }

        public boolean containsKey(K key) {
            return map.containsKey(key);
        }

        public Map<K, C> getMap() {
            return this.map;
        }
    }

    public static class GroupingListMap<K, V> extends GroupingMap<K, V, List<V>> {

        @Override
        protected List<V> newCollection() {
            return new ArrayList<>();
        }
    }

    public static class GroupingHashSetMap<K, V> extends GroupingMap<K, V, HashSet<V>> {
        @Override
        protected HashSet<V> newCollection() {
            return new HashSet<>();
        }
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
        //SortedSet<Commit> allCommits = revisionsReader.getCommits();
        Set<String> allCommits = commitsDistanceDb.getCommits();

        ProjectInformationReader<ListChangedFunctionsConfig> projectInfo = new ProjectInformationReader<>(config);
        LOG.debug("Reading project information");
        projectInfo.readSnapshotsAndRevisionsFile();
        LOG.debug("Done reading project information");

        Collection<Date> snapshotDates = getDatesOfActualSnapshots(projectInfo);
        maybeAddDummySnapshotToCoverRemainingChanges(snapshotDates);

        changesByCommit = new GroupingListMap<>();
        changesByFunction = new GroupingListMap<>();

        LOG.debug("Reading all function changes.");
        readFunctionsChangedInSnapshots(snapshotDates);
        LOG.debug("Done reading all function changes. Number of distinct changed functions: " + changesByFunction.getMap().size());

        //adds = new LinkedHashMap<>();
        //dels = new LinkedHashMap<>();
//        LOG.debug("Extracting function additions and deletions from " + allCommits.size() + " commits.");
//        extractFunctionAdditionsAndDeletions(allCommits);
//        LOG.debug("Done extracting function additions and deletions. Detected " + adds.size() + " additions and " + dels.size() + " corresponding deletions.");

        parseRenames();

        int ageRequests = 0, actualAge = 0, guessedAge = 0, guessed0Age = 0, noAgeAtAll = 0;
        int missingAdditions = 0;

        System.out.println("MOD_TYPE,FUNCTION_SIGNATURE,FILE,COMMIT,AGE,DIST");
        for (Map.Entry<FunctionId, List<FunctionChangeRow>> e : changesByFunction.getMap().entrySet()) {
            final FunctionId function = e.getKey();
            final List<FunctionChangeRow> directChanges = e.getValue();
            final Set<String> directCommitIds = new HashSet<>();
            for (FunctionChangeRow r : directChanges) {
                directCommitIds.add(r.commitId);
            }
            final Set<FunctionId> functionAliases = getFunctionAliases(function, directCommitIds);

            // All the commits that have created this function or a previous version of it.
            Set<String> knownAddsForFunction = getAddingCommitsIncludingAliases(functionAliases);

            Set<String> commitsToFunctionAndAliases = getCommitsToFunctionIncludingAliases(functionAliases);
            final Set<String> guessedAddsForFunction = filterAncestorCommits(commitsToFunctionAndAliases);
            guessedAddsForFunction.removeAll(knownAddsForFunction);

            if (knownAddsForFunction.isEmpty()) {
                missingAdditions++;
                LOG.warn("No known creating commits for function '" + function + "'.");
            }

            if (!guessedAddsForFunction.isEmpty()) {
                LOG.warn("Some unknown creating commits for function '" + function +
                        "'. Assuming the following additional creating commits: " + guessedAddsForFunction);
            }

            Map<String, AgeAndDistanceStrings> knownDistancesCache = new HashMap<>();

            for (FunctionChangeRow change : directChanges) {
                final String currentCommit = change.commitId;
                AgeAndDistanceStrings distanceStrings = knownDistancesCache.get(currentCommit);
                if (distanceStrings == null) {
                    int minDist;
                    if (knownAddsForFunction.contains(currentCommit)) {
                        minDist = 0;
                    } else {
                        minDist = Integer.MAX_VALUE;
                        for (String otherCommit : commitsToFunctionAndAliases) {
                            if (currentCommit.equals(otherCommit)) continue;
                            Optional<Integer> currentDist = commitsDistanceDb.minDistance(currentCommit, otherCommit);
                            if (currentDist.isPresent()) {
                                int currentDistValue = currentDist.get();
                                if (currentDistValue == 0) {
                                    LOG.warn("Distance between commits is 0? " + currentCommit + " .. " + otherCommit);
                                }
                                minDist = Math.min(minDist, currentDistValue);
                            }
                        }
                    }
                    String minDistStr = minDist < Integer.MAX_VALUE ? Integer.toString(minDist) : "";
                    Optional<Integer> age;
                    ageRequests++;
                    if (knownAddsForFunction.contains(currentCommit)) {
                        age = Optional.of(0);
                    } else {
                        age = Optional.empty();
                        for (String addingCommit : knownAddsForFunction) {
                            age = commitsDistanceDb.minDistance(currentCommit, addingCommit);
                            if (age.isPresent()) {
                                actualAge++;
                                break;
                            }
                        }

                        if (!age.isPresent()) {
                            for (String addingCommit : guessedAddsForFunction) {
                                age = commitsDistanceDb.minDistance(currentCommit, addingCommit);
                                if (age.isPresent()) {
                                    int ageValue = age.get();
                                    if (ageValue == 0) {
                                        age = Optional.empty();
                                        continue;
                                    } else {
                                        guessedAge++;
                                        LOG.warn("Forced to assume alternative adding commit. Function: " + function + " Current commit: " + currentCommit + " Assumed creating commit: " + addingCommit);
//                                    if (ageValue == 0) {
//                                        LOG.warn("Age of function at commit is 0? Function: " + function + " Current commit: " + currentCommit + " Assumed creating commit: " + addingCommit);
//                                        guessed0Age++;
//                                    }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    String ageStr;
                    if (age.isPresent()) {
                        ageStr = age.get().toString();
                    } else {
                        noAgeAtAll++;
                        ageStr = "";
                    }
                    distanceStrings = new AgeAndDistanceStrings(ageStr, minDistStr);
                    knownDistancesCache.put(currentCommit, distanceStrings);
                }

                System.out.println(change.modType + ",\"" + function.signature + "\"," + function.file + "," + currentCommit + "," + distanceStrings.ageString + "," + distanceStrings.distanceString);
            }
        }
        LOG.debug("Functions with unknown additions: " + missingAdditions);
        LOG.debug("Found ages: ageRequests: " + ageRequests + " actualAge: " + actualAge + " guessedAge: " + guessedAge + " guessed0Age: " + guessed0Age +
                " noAgeAtAll: " + noAgeAtAll);
    }

    private Set<FunctionId> getFunctionAliases(FunctionId function, Set<String> directCommitIds) {
        Set<FunctionId> functionAliases = new HashSet<>();
        for (String commit : filterAncestorCommits(directCommitIds)) {
            Set<FunctionId> currentAliases = getAllOlderFunctionIds(function, commit);
            functionAliases.addAll(currentAliases);
        }
        return functionAliases;
    }

    private Set<String> getCommitsToFunctionIncludingAliases(Set<FunctionId> functionAliases) {
        Set<String> result = new LinkedHashSet<>();
        for (FunctionId alias : functionAliases) {
            List<FunctionChangeRow> aliasChanges = changesByFunction.get(alias);
            if (aliasChanges == null) continue;
            for (FunctionChangeRow change : aliasChanges) {
                result.add(change.commitId);
            }
        }
        return result;
    }

    private Set<String> getAddingCommitsIncludingAliases(Set<FunctionId> functionAliases) {
        Set<String> result = new LinkedHashSet<>();
        for (FunctionId alias : functionAliases) {
            List<FunctionChangeRow> aliasChanges = changesByFunction.get(alias);
            if (aliasChanges == null) continue;
            for (FunctionChangeRow change : aliasChanges) {
                if (change.modType == FunctionChangeHunk.ModificationType.ADD) {
                    result.add(change.commitId);
                }
            }
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

    private Set<String> filterAncestorCommits(Set<String> allCommits) {
        // Determine all commits that are not descendants of other commits.
        Set<String> commitsWithoutAncestors = new HashSet<>(allCommits);
        for (Iterator<String> it = commitsWithoutAncestors.iterator(); it.hasNext(); ) {
            final String descendant = it.next();
            for (String ancestor : allCommits) {
                if (ancestor.equals(descendant)) continue;
                if (commitsDistanceDb.isDescendant(descendant, ancestor)) {
                    it.remove();
                    break;
                }
            }
        }

        return commitsWithoutAncestors;
    }

    private String guessCreatingCommit(Set<String> allCommits) {
        // Determine for each commit how often it is true that is an ancestor of the other commits.
        int winningNumberOfDescendants = -1;
        String winningCommit = null;
        for (String ancestor : allCommits) {
            int numDescendants = 0;
            for (String descendant : allCommits) {
                if (ancestor.equals(descendant)) continue;

                if (commitsDistanceDb.isDescendant(descendant, ancestor)) {
                    numDescendants++;
                }
            }

            if (numDescendants > winningNumberOfDescendants) {
                winningCommit = ancestor;
                winningNumberOfDescendants = numDescendants;
            }
        }

        return winningCommit;
    }

//    private void extractFunctionAdditionsAndDeletions(Set<String> allCommits) {
//        for (String commitHash : allCommits) {
//            List<FunctionChangeRow> changes = changesByCommit.get(commitHash);
//            if (changes == null) continue;
//            //LOG.debug("Commit " + c.getHash() + " changes " + changes.size() + " function(s).");
//            for (FunctionChangeRow ch : changes) {
//                switch (ch.modType) {
//                    case ADD: {
//                        FunctionId key = ch.functionId;
//                        if (adds.containsKey(key)) {
//                            LOG.warn("Function is added a second time. Ignoring addition: " + key);
//                        } else {
//                            //LOG.debug("Function " + key + " is added by change " + ch);
//                            adds.put(key, ch);
//                        }
//                        break;
//                    }
//                    case DEL: {
//                        FunctionId key = ch.functionId;
//                        if (dels.containsKey(key)) {
//                            LOG.warn("Function is deleted a second time. Ignoring deletion: " + key);
//                        } else {
//                            if (!adds.containsKey(key)) {
//                                LOG.warn("Function appears to be deleted before it has been added: " + key);
//                            }
//                            //LOG.debug("Function " + key + " is deleted by change " + ch);
//                            dels.put(key, ch);
//                        }
//                        break;
//                    }
//                }
//            }
//        }
//    }

    private void readFunctionsChangedInSnapshots(Collection<Date> snapshotsToProcesses) {
        LOG.debug("Reading function changes for " + snapshotsToProcesses.size() + " snapshot(s).");
        int numChanges = 0;
        for (Date snapshotDate : snapshotsToProcesses) {
            Collection<FunctionChangeRow> functionChanges = readFunctionsChangedInSnapshot(snapshotDate);
            for (FunctionChangeRow change : functionChanges) {
                changesByFunction.put(change.functionId, change);
                changesByCommit.put(change.commitId, change);
                numChanges++;
            }
        }
        LOG.debug("Read " + numChanges + " function change(s).");
    }

    private void parseRenames() {
        LOG.debug("Parsing all renames/moves/signature changes");
        int numMoves = 0;
        for (Collection<FunctionChangeRow> changes : changesByCommit.getMap().values()) {
            for (FunctionChangeRow change : changes) {
                if (change.modType != FunctionChangeHunk.ModificationType.MOVE) continue;
                numMoves++;
                if (!change.newFunctionId.isPresent()) {
                    throw new RuntimeException("Encountered a MOVE without a new function id: " + change);
                }
                movesByNewFunctionId.put(change.newFunctionId.get(), change);
            }
        }

        if (LOG.isDebugEnabled()) {
            for (Map.Entry<FunctionId, HashSet<FunctionChangeRow>> e : movesByNewFunctionId.getMap().entrySet()) {
                FunctionId functionId = e.getKey();
                HashSet<FunctionChangeRow> moves = e.getValue();
                for (FunctionChangeRow move : moves) {
                    LOG.debug(functionId + " <- " + move.functionId);
                }
            }
        }

        LOG.debug("Parsed " + numMoves + " MOVE events.");
    }

    private Set<FunctionId> getAllOlderFunctionIds(FunctionId id, final String commit) {
//        LOG.debug("getAllOlderFunctionIds " + id + " " + commit);
        Queue<FunctionId> todo = new LinkedList<>();
        todo.add(id);
        Set<FunctionId> done = new LinkedHashSet<>();
        FunctionId needle;
        while ((needle = todo.poll()) != null) {
            done.add(needle);

            Set<FunctionChangeRow> moves = movesByNewFunctionId.get(needle);
            if (moves == null) {
//                LOG.debug("No moves whatsoever for " + needle);
                continue;
            }
            for (FunctionChangeRow r : moves) {
                String ancestorCommit = r.commitId;
                FunctionId oldId = r.functionId;
                if (!todo.contains(oldId) && !done.contains(oldId)) {
                    if (commitsDistanceDb.isDescendant(commit, ancestorCommit)) {
                        todo.add(oldId);
                    } else {
//                        LOG.debug("Rejecting move " + r + ": not an ancestor of " + commit);
                    }
                }
            }
        }

//        if (LOG.isDebugEnabled()) {
//            String size;
//            Set<FunctionId> aliases = new LinkedHashSet<>(done);
//            done.remove(id);
//            switch (aliases.size()) {
//                case 0:
//                    size = "no aliases";
//                    break;
//                case 1:
//                    size = "1 alias";
//                    break;
//                default:
//                    size = aliases.size() + " aliases";
//                    break;
//            }
//            LOG.debug("getAllOlderFunctionIds(" + id + ", " + commit + ") found " +
//                    size + ": " + aliases);
//        }
        return done;
    }

    public static class FunctionId {
        public final String signature;
        public final String file;


        public FunctionId(String signature, String file) {
            this.signature = signature;
            this.file = file;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FunctionId)) return false;

            FunctionId that = (FunctionId) o;

            if (!signature.equals(that.signature)) return false;
            return file.equals(that.file);
        }

        @Override
        public int hashCode() {
            int result = signature.hashCode();
            result = 31 * result + file.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "FunctionId{" +
                    "signature='" + signature + '\'' +
                    ", file='" + file + '\'' +
                    '}';
        }
    }

    public static class FunctionChangeRow {
        public FunctionId functionId;
        public String commitId;
        public FunctionChangeHunk.ModificationType modType;
        public Optional<FunctionId> newFunctionId;

        @Override
        public String toString() {
            return "FunctionChangeRow{" +
                    "modType=" + modType +
                    ", commitId='" + commitId + '\'' +
                    ", functionId=" + functionId +
                    ", newFunctionId=" + (newFunctionId.isPresent() ? newFunctionId : "") +
                    '}';
        }
    }

    private List<FunctionChangeRow> readFunctionsChangedInSnapshot(Date snapshotDate) {
        SimpleCsvFileReader<List<FunctionChangeRow>> r = new SimpleCsvFileReader<List<FunctionChangeRow>>() {
            List<FunctionChangeRow> results = new ArrayList<>();

            @Override
            protected boolean hasHeader() {
                return true;
            }

            @Override
            protected void processHeader(String[] headerLine) {
                final int minCols = FunctionChangeHunksColumns.values().length;
                if (headerLine.length < minCols) {
                    throw new RuntimeException("Not enough columns. Expected at least " + minCols + ", got " + headerLine.length);
                }

                for (int col = 0; col < minCols; col++) {
                    String expectedColName = FunctionChangeHunksColumns.values()[col].name();
                    if (!headerLine[col].equalsIgnoreCase(expectedColName)) {
                        throw new RuntimeException("Column name mismatch. Expected column " + col + " to be " + expectedColName + ", got: " + headerLine[col]);
                    }
                }
            }

            @Override
            protected void processContentLine(String[] line) {
                FunctionChangeRow result = new FunctionChangeRow();
                String signature = line[FunctionChangeHunksColumns.FUNCTION_SIGNATURE.ordinal()];
                String file = line[FunctionChangeHunksColumns.FILE.ordinal()];
                FunctionId functionId = new FunctionId(signature, file);
                result.functionId = functionId;
                result.commitId = line[FunctionChangeHunksColumns.COMMIT_ID.ordinal()];
                String modTypeName = line[FunctionChangeHunksColumns.MOD_TYPE.ordinal()];
                result.modType = FunctionChangeHunk.ModificationType.valueOf(modTypeName);
                result.newFunctionId = parseNewFunctionId(line);

                results.add(result);
            }

            private Optional<FunctionId> parseNewFunctionId(String[] line) {
                final Optional<FunctionId> newFunctionId;
                String newSignature = line[FunctionChangeHunksColumns.NEW_FUNCTION_SIGNATURE.ordinal()];
                String newFile = line[FunctionChangeHunksColumns.NEW_FILE.ordinal()];
                boolean noNewSignature = (newSignature == null) || newSignature.isEmpty();
                boolean noNewFile = (newFile == null) || (newFile.isEmpty());
                if (noNewFile != noNewSignature) {
                    throw new RuntimeException("New signature and new file must both be set or not at all! Erroneous line: " + line);
                }
                if (noNewFile) {
                    newFunctionId = Optional.empty();
                } else {
                    newFunctionId = Optional.of(new FunctionId(newSignature, newFile));
                }
                return newFunctionId;
            }

            @Override
            protected List<FunctionChangeRow> finalizeResult() {
                return results;
            }
        };

        File f = new File(config.snapshotResultsDirForDate(snapshotDate), FunctionChangeHunksColumns.FILE_BASENAME);

        return r.readFile(f);
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
