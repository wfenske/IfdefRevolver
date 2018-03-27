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
    private Map<FunctionId, FunctionChangeRow> adds;
    private Map<FunctionId, FunctionChangeRow> dels;
    private GroupingListMap<String, FunctionChangeRow> changesByCommit;
    private GroupingListMap<FunctionId, FunctionChangeRow> changesByFunction;

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
        adds = new LinkedHashMap<>();
        dels = new LinkedHashMap<>();

        LOG.debug("Reading all function changes.");
        readFunctionsChangedInSnapshots(snapshotDates);
        LOG.debug("Done reading all function changes. Number of distinct changed functions: " + changesByFunction.getMap().size());

        LOG.debug("Extracting function additions and deletions from " + allCommits.size() + " commits.");
        extractFunctionAdditionsAndDeletions(allCommits);
        LOG.debug("Done extracting function additions and deletions. Detected " + adds.size() + " additions and " + dels.size() + " corresponding deletions.");

        int successes = 0;
        int failures = 0;
        int missingAdditions = 0;
        System.out.println("MOD_TYPE,FUNCTION_SIGNATURE,FILE,COMMIT,AGE,DIST");
        for (Map.Entry<FunctionId, List<FunctionChangeRow>> e : changesByFunction.getMap().entrySet()) {
            final FunctionId function = e.getKey();
            List<FunctionChangeRow> changes = new LinkedList<>(e.getValue());
            Set<String> commitsToFunction = new HashSet<>(changes.size());
            Set<String> addsForFunction = new LinkedHashSet<>();
            for (FunctionChangeRow change : changes) {
                commitsToFunction.add(change.commitId);
                if (change.modType == FunctionChangeHunk.ModificationType.ADD) {
                    addsForFunction.add(change.commitId);
                }
            }

            //final String creatingCommit;
            //FunctionChangeRow addition = adds.get(function);
            boolean additionMissing = true;
            //if (addition != null) {
            //creatingCommit = addition.commitId;
            //} else {
            if (!addsForFunction.isEmpty()) {
                //LOG.warn("Adds not extracted, although there were some. Function: " + function);
                //addition = addsForFunction.iterator().next();
                //creatingCommit = guessCreatingCommit(commitsToFunction);
                //creatingCommit = addsForFunction.iterator().next();
            } else {
                //creatingCommit = guessCreatingCommit(changes);
                //creatingCommit = guessCreatingCommit(commitsToFunction);
                //LOG.warn("Assumed creating commit is " + creatingCommit);
                addsForFunction = guessAddsForFunction(commitsToFunction);
            }

            if (addsForFunction.isEmpty()) {
                missingAdditions++;
                LOG.warn("Exact addition of function unknown. Guessing addition instead. Function: " + function);
            }

            //}
            Set<String> commitsAlreadySeen = new HashSet<>();
            for (FunctionChangeRow change : changes) {
                final String currentCommit = change.commitId;
                if (commitsAlreadySeen.contains(currentCommit)) continue;
                else commitsAlreadySeen.add(currentCommit);

                int minDist;
                if (addsForFunction.contains(currentCommit)) {
                    minDist = 0;
                } else {
                    minDist = Integer.MAX_VALUE;
                    for (String otherCommit : commitsToFunction) {
                        if (currentCommit.equals(otherCommit)) continue;
                        Optional<Integer> currentDist = commitsDistanceDb.minDistance(currentCommit, otherCommit);
                        if (currentDist.isPresent()) {
                            minDist = Math.min(minDist, currentDist.get());
                        }
                    }
                }
                String minDistStr = minDist < Integer.MAX_VALUE ? Integer.toString(minDist) : "";
                //Optional<Integer> age = commitsDistanceDb.minDistance(currentCommit, creatingCommit);
                //if (!age.isPresent()) {
                Optional<Integer> age = Optional.empty();
                for (String addingCommit : addsForFunction) {
                    age = commitsDistanceDb.minDistance(currentCommit, addingCommit);
                    if (age.isPresent()) {
                        //LOG.info("Determined function age by referring to a different adding commit. Yeah!");
                        break;
                    }
                }
                //}
                final String ageStr;
                if (age.isPresent()) {
                    successes++;
                    ageStr = age.get().toString();
                } else {
                    failures++;
                    ageStr = "";
                }
                System.out.println(change.modType + ",\"" + function.signature + "\"," + function.file + "," + currentCommit + "," + ageStr + "," + minDistStr);
            }
        }
        LOG.debug("Determined function age " + successes + " time(s). Failed " + failures + " time(s). Functions with unknown additions: " + missingAdditions);
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

    private Set<String> guessAddsForFunction(Set<String> allCommits) {
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

    private void extractFunctionAdditionsAndDeletions(Set<String> allCommits) {
        for (String commitHash : allCommits) {
            List<FunctionChangeRow> changes = changesByCommit.get(commitHash);
            if (changes == null) continue;
            //LOG.debug("Commit " + c.getHash() + " changes " + changes.size() + " function(s).");
            for (FunctionChangeRow ch : changes) {
                switch (ch.modType) {
                    case ADD: {
                        FunctionId key = ch.functionId;
                        if (adds.containsKey(key)) {
                            LOG.warn("Function is added a second time. Ignoring addition: " + key);
                        } else {
                            //LOG.debug("Function " + key + " is added by change " + ch);
                            adds.put(key, ch);
                        }
                        break;
                    }
                    case DEL: {
                        FunctionId key = ch.functionId;
                        if (dels.containsKey(key)) {
                            LOG.warn("Function is deleted a second time. Ignoring deletion: " + key);
                        } else {
                            if (!adds.containsKey(key)) {
                                LOG.warn("Function appears to be deleted before it has been added: " + key);
                            }
                            //LOG.debug("Function " + key + " is deleted by change " + ch);
                            dels.put(key, ch);
                        }
                        break;
                    }
                }
            }
        }
    }

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
        public String newFile;
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
                result.newFile = line[FunctionChangeHunksColumns.NEW_FILE.ordinal()];
                results.add(result);
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
