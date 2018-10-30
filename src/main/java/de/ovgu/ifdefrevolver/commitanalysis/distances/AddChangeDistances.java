package de.ovgu.ifdefrevolver.commitanalysis.distances;

import com.google.common.collect.Ordering;
import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.input.ProjectInformationReader;
import de.ovgu.ifdefrevolver.bugs.correlate.main.ProjectInformationConfig;
import de.ovgu.ifdefrevolver.bugs.correlate.output.JointFunctionAbSmellAgeSnapshotColumns;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.input.RevisionsCsvReader;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDbCsvReader;
import de.ovgu.ifdefrevolver.commitanalysis.*;
import de.ovgu.ifdefrevolver.util.*;
import de.ovgu.skunk.detection.output.CsvEnumUtils;
import de.ovgu.skunk.detection.output.CsvFileWriterHelper;
import de.ovgu.skunk.detection.output.CsvRowProvider;
import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.rank.Max;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.descriptive.rank.Min;
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
    private Map<Date, List<AbResRow>> annotationDataInSnapshots;
    private List<List<FunctionIdWithCommit>> functionGenealogies;

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

        LOG.debug("Building move resolver.");
        this.moveResolver = buildMoveResolver(changesInSnapshots);

        final Set<Map.Entry<FunctionId, List<FunctionChangeRow>>> functionChangeEntries =
                moveResolver.getChangesByFunction().entrySet();
        LOG.debug("Done reading all function changes. Number of distinct changed functions: " + functionChangeEntries.size());

        allFunctionsInSnapshots = readAllFunctionsInSnapshots(realSnapshotDates);
        annotationDataInSnapshots = readAllAbResInSnapshots(realSnapshotDates);

        Set<FunctionIdWithCommit> allFunctionsEver = getFunctionIdsWithCommitFromRegularSnapshots();
        Set<FunctionIdWithCommit> leftOverFunctionIdsWithCommits = getFunctionIdsWithCommitFromLeftOverSnapshot(leftOverSnapshotDate);
        Set<FunctionIdWithCommit> functionsAddedInBetween = getFunctionIdsWithCommitsAddedInBetween();

        functionGenealogies = computeFunctionGenealogies(allFunctionsEver, leftOverFunctionIdsWithCommits, functionsAddedInBetween);
        List<SnapshotWithFunctions> snapshotsWithFunctions = mergeGenealogiesWithSnapshotData();
        writeAbSmellAgeSnapshotCsv(snapshotsWithFunctions);
        System.exit(0);

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

    private void writeAbSmellAgeSnapshotCsv(List<SnapshotWithFunctions> snapshotsWithFunctions) {
        ProgressMonitor pm = new ProgressMonitor(projectInfo.getSnapshots().size()) {
            @Override
            protected void reportIntermediateProgress() {
                LOG.info("Wrote CSV with merged snapshot data " + this.ticksDone + "/" + this.ticksTotal + " (" + this.numberOfCurrentReport + "%)");
            }

            @Override
            protected void reportFinished() {
                LOG.info("Wrote all " + this.ticksTotal + " CSVs with merged snapshot data.");
            }
        };

        for (SnapshotWithFunctions snapshot : snapshotsWithFunctions) {
            writeAbSmellAgeSnapshotCsv(snapshot);
            pm.increaseDone();
        }
    }

    private void writeAbSmellAgeSnapshotCsv(SnapshotWithFunctions snapshot) {
        CsvFileWriterHelper writerHelper = new CsvFileWriterHelper() {
            @Override
            protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
                final Object[] headerRow = CsvEnumUtils.headerRow(JointFunctionAbSmellAgeSnapshotColumns.class);
                csv.printRecord(headerRow);
                CsvRowProvider<SnapshotFunctionGenealogy, Void, JointFunctionAbSmellAgeSnapshotColumns> rowProvider = new CsvRowProvider<>(JointFunctionAbSmellAgeSnapshotColumns.class, null);

                for (SnapshotFunctionGenealogy functionGenealogy : snapshot.functions.values()) {
                    Object[] row = rowProvider.dataRow(functionGenealogy);
                    csv.printRecord(row);
                }
            }
        };

        File resultFile = new File(config.snapshotResultsDirForDate(snapshot.snapshot.getSnapshotDate()),
                JointFunctionAbSmellAgeSnapshotColumns.FILE_BASENAME);
        writerHelper.write(resultFile);
    }

    private List<SnapshotWithFunctions> mergeGenealogiesWithSnapshotData() {
        List<SnapshotWithFunctions> result = new ArrayList<>();

        GroupingListMap<Commit, List<FunctionIdWithCommit>> genealogiesByStartCommit = groupFunctionGenealogiesBySnapshortStartCommit();

        ProgressMonitor pm = new ProgressMonitor(projectInfo.getSnapshots().size()) {
            @Override
            protected void reportIntermediateProgress() {
                LOG.info("Merged genealogies into " + this.ticksDone + "/" + this.ticksTotal + " snapshots (" + this.numberOfCurrentReport + "%)");
            }

            @Override
            protected void reportFinished() {
                LOG.info("Merged genealogies into all " + this.ticksTotal + " snapshots.");
            }
        };

        for (Snapshot s : projectInfo.getSnapshots().values()) {
            Commit startCommit = commitsDistanceDb.internCommit(s.getStartHash());
            List<List<FunctionIdWithCommit>> genealogiesForSnapshot = genealogiesByStartCommit.get(startCommit);
            if (genealogiesForSnapshot == null) {
                LOG.warn("No genealogy for snapshot " + s);
                genealogiesForSnapshot = Collections.emptyList();
            }
            SnapshotWithFunctions swf = mergeGenealogiesWithSnapshotData(s, genealogiesForSnapshot);
            result.add(swf);
            pm.increaseDone();
        }

        return result;
    }

    private GroupingListMap<Commit, List<FunctionIdWithCommit>> groupFunctionGenealogiesBySnapshortStartCommit() {
        Set<Commit> startCommits = getAllStartCommits();

        GroupingListMap<Commit, List<FunctionIdWithCommit>> genealogiesByStartCommit = new GroupingListMap<>();

        for (List<FunctionIdWithCommit> genealogy : this.functionGenealogies) {
            Set<Commit> startCommitsInGenealogy = new HashSet<>();
            genealogy.forEach((f) -> startCommitsInGenealogy.add(f.commit));
            startCommitsInGenealogy.retainAll(startCommits);
            startCommitsInGenealogy.forEach((startCommit) -> genealogiesByStartCommit.put(startCommit, genealogy));
//            for (Commit startCommit : startCommitsInGenealogy) {
//                String commitHash = startCommit.commitHash;
//                if (commitHash.equals("d620d4368a7ee17d60f2b381d4c80b22c68ba8e2")) {
//                    LOG.debug("Saving genealogy for hash " + startCommit);
//                }
//                genealogiesByStartCommit.put(startCommit, genealogy);
//            }
        }

        return genealogiesByStartCommit;
    }

    private Set<Commit> getAllStartCommits() {
        Set<Commit> startCommits = new HashSet<>();
        for (Snapshot s : projectInfo.getSnapshots().values()) {
            Commit startCommit = commitsDistanceDb.internCommit(s.getStartHash());
            startCommits.add(startCommit);
        }
        return startCommits;
    }

    private SnapshotWithFunctions mergeGenealogiesWithSnapshotData(Snapshot s, List<List<FunctionIdWithCommit>> genealogiesForSnapshot) {
        final Date snapshotDate = s.getSnapshotDate();

        Map<FunctionId, AllFunctionsRow> allFunctionsByFunctionId = new LinkedHashMap<>();
        for (AllFunctionsRow r : allFunctionsInSnapshots.get(snapshotDate)) {
            allFunctionsByFunctionId.put(r.functionId, r);
        }

        Map<FunctionId, AbResRow> abResByFunctionId = new HashMap<>();
        for (AbResRow r : annotationDataInSnapshots.get(snapshotDate)) {
            abResByFunctionId.put(r.getFunctionId(), r);
        }

        GroupingListMap<FunctionId, FunctionChangeRow> changesByFunctionId = new GroupingListMap<>();
        for (FunctionChangeRow r : changesInSnapshots.get(snapshotDate)) {
            changesByFunctionId.put(r.functionId, r);
        }

        final Commit startCommit = commitsDistanceDb.internCommit(s.getStartHash());

        Set<Commit> commitsInSnapshot = new LinkedHashSet<>();
        for (String h : s.getCommitHashes()) {
            commitsInSnapshot.add(commitsDistanceDb.internCommit(h));
        }

        Comparator<FunctionIdWithCommit> bySnapshotComparator = getBySnapshotCommitOrderComparator(commitsInSnapshot);

        Map<FunctionId, List<FunctionIdWithCommit>> genealogiesByFirstFunctionId = new HashMap<>();
        for (List<FunctionIdWithCommit> genealogy : genealogiesForSnapshot) {
            List<FunctionIdWithCommit> cutDownToSnapshot = limitGenealogyToSnapshot(genealogy, commitsInSnapshot);
            Collections.sort(cutDownToSnapshot, bySnapshotComparator);

            FunctionIdWithCommit firstFunctionIdWithCommit = cutDownToSnapshot.get(0);
            if (!firstFunctionIdWithCommit.commit.equals(startCommit)) {
                LOG.warn("First function id with commit does not match snapshot start hash: " + firstFunctionIdWithCommit + " vs. " + startCommit);
                continue;
            }

            final FunctionId firstFunctionId = firstFunctionIdWithCommit.functionId;
            if (genealogiesByFirstFunctionId.containsKey(firstFunctionId)) {
                LOG.warn("Snapshot already contains the same function id: " + firstFunctionId);
                continue;
            }

            for (FunctionIdWithCommit fiwc : cutDownToSnapshot) {
                Commit c = fiwc.commit;
                if (!c.equals(startCommit)) break;
                genealogiesByFirstFunctionId.put(fiwc.functionId, cutDownToSnapshot);
            }
        }

        final int numAllUniqueFunctions = allFunctionsByFunctionId.size();
        final int numAllFunctions = allFunctionsInSnapshots.get(snapshotDate).size();
        final int numGenealogies = genealogiesForSnapshot.size();

        if ((numAllFunctions != numAllUniqueFunctions) || (numAllFunctions != numGenealogies)) {
            Set<FunctionId> uncoveredAllFunctionIds = new HashSet<>(allFunctionsByFunctionId.keySet());
            Set<FunctionId> uncoveredGenealogies = new HashSet<>(genealogiesByFirstFunctionId.keySet());

            uncoveredAllFunctionIds.removeAll(genealogiesByFirstFunctionId.keySet());
            uncoveredGenealogies.removeAll(allFunctionsByFunctionId.keySet());

            LOG.warn("Mismatch in numbers of functions and genealogies: "
                    + " #allFunctions=" + numAllFunctions
                    + " #allUniqueFunctions=" + numAllUniqueFunctions
                    + " #genealogies=" + numGenealogies
                    + " uncoveredAllFunctionIds=" + uncoveredAllFunctionIds
                    + " uncoveredGenealogies=" + uncoveredGenealogies);
        }

        Map<FunctionId, SnapshotFunctionGenealogy> genealogiesByFunctionId = new HashMap<>();

        for (AllFunctionsRow allFunctionsRow : allFunctionsByFunctionId.values()) {
            final FunctionId firstFunctionId = allFunctionsRow.functionId;
            List<FunctionIdWithCommit> genealogy = genealogiesByFirstFunctionId.get(firstFunctionId);
            if (genealogy == null) {
                LOG.warn("No genealogy for function id " + firstFunctionId);
                continue;
            }

            Set<FunctionId> allDefinitions = new LinkedHashSet<>();
            FunctionId lastFunctionId = null;
            for (FunctionIdWithCommit f : genealogy) {
                allDefinitions.add(f.functionId);
                lastFunctionId = f.functionId;
            }

            Set<FunctionChangeRow> changes = new LinkedHashSet<>();
            for (FunctionId id : allDefinitions) {
                List<FunctionChangeRow> changesForId = changesByFunctionId.get(id);
                if (changesForId != null) {
                    changes.addAll(changesForId);
                }
            }

            AgeAndDistance ageAndDistance = getAgeAndDistance(firstFunctionId, startCommit);
            AbResRow abResRow = abResByFunctionId.get(firstFunctionId);
            if (abResRow == null) abResRow = AbResRow.dummyRow(firstFunctionId, allFunctionsRow.loc);

            SnapshotFunctionGenealogy sfg = new SnapshotFunctionGenealogy();
            sfg.commitsSinceCreation = ageAndDistance.age;
            sfg.commitsSinceLastEdit = ageAndDistance.distance;
            sfg.annotationData = abResRow;
            sfg.definitions = allDefinitions;
            sfg.snapshot = s;
            sfg.snapshotStartCommit = startCommit;
            sfg.changes = changes;
            sfg.functionIdAtStart = firstFunctionId;
            sfg.functionIdAtEnd = lastFunctionId;

            SnapshotFunctionGenealogy previousEntry = genealogiesByFunctionId.put(firstFunctionId, sfg);
            if (previousEntry != null) {
                LOG.warn("Genealogy for function-id " + firstFunctionId +
                        " has replaced a previous genealogy.");
            }
        }

        SnapshotWithFunctions result = new SnapshotWithFunctions();
        result.snapshot = s;
        result.functions = genealogiesByFunctionId;

        return result;
    }

    private Comparator<FunctionIdWithCommit> getBySnapshotCommitOrderComparator(Set<Commit> commitsInSnapshot) {
        Ordering<Commit> commitsInSnapshotOrdering = Ordering.explicit(new ArrayList<>(commitsInSnapshot));
        return new Comparator<FunctionIdWithCommit>() {
            @Override
            public int compare(FunctionIdWithCommit o1, FunctionIdWithCommit o2) {
                return commitsInSnapshotOrdering.compare(o1.commit, o2.commit);
            }
        };
    }

    private List<FunctionIdWithCommit> limitGenealogyToSnapshot(List<FunctionIdWithCommit> genealogy, Set<Commit> commitsInSnapshot) {
        List<FunctionIdWithCommit> cutDownToSnapshot = new ArrayList<>();
        for (FunctionIdWithCommit f : genealogy) {
            if (commitsInSnapshot.contains(f.commit)) {
                cutDownToSnapshot.add(f);
            }
        }
        return cutDownToSnapshot;
    }

    public static class SnapshotFunctionGenealogy {
        private FunctionId functionIdAtStart;
        private FunctionId functionIdAtEnd;
        private Optional<Integer> commitsSinceCreation;
        private Optional<Integer> commitsSinceLastEdit;
        private Snapshot snapshot;
        private Commit snapshotStartCommit;
        private Set<FunctionId> definitions;
        private Set<FunctionChangeRow> changes;
        private AbResRow annotationData;

        public FunctionId getFunctionIdAtStart() {
            return functionIdAtStart;
        }

        public FunctionId getFunctionIdAtEnd() {
            return functionIdAtEnd;
        }

        public Optional<Integer> getCommitsSinceCreation() {
            return commitsSinceCreation;
        }

        public Optional<Integer> getCommitsSinceLastEdit() {
            return commitsSinceLastEdit;
        }

        public Snapshot getSnapshot() {
            return snapshot;
        }

        public Commit getSnapshotStartCommit() {
            return snapshotStartCommit;
        }

        public Set<FunctionId> getDefinitions() {
            return definitions;
        }

        public Set<FunctionChangeRow> getChanges() {
            return changes;
        }

        public AbResRow getAnnotationData() {
            return annotationData;
        }
    }

    private static class SnapshotWithFunctions {
        Snapshot snapshot;
        Map<FunctionId, SnapshotFunctionGenealogy> functions;
    }

    private Set<FunctionIdWithCommit> getFunctionIdsWithCommitsAddedInBetween() {
        Set<FunctionIdWithCommit> functionIdsAddedInBetween = new LinkedHashSet<>();
        for (List<FunctionChangeRow> changesInSnapshot : changesInSnapshots.values()) {
            for (FunctionChangeRow r : changesInSnapshot) {
                final FunctionIdWithCommit idWithCommit;
                switch (r.modType) {
                    case ADD:
                        idWithCommit = new FunctionIdWithCommit(r.functionId, r.commit, false);
                        break;
                    default:
                        continue;
                }
                functionIdsAddedInBetween.add(idWithCommit);
            }
        }
        return functionIdsAddedInBetween;
    }

    private Set<FunctionIdWithCommit> getFunctionIdsWithCommitFromLeftOverSnapshot(Optional<Date> leftOverSnapshotDate) {
        if (!leftOverSnapshotDate.isPresent()) return Collections.emptySet();
        Set<FunctionIdWithCommit> leftOverFunctionIdsWithCommits = new LinkedHashSet<>();
        Date leftOverSnapshotDateValue = leftOverSnapshotDate.get();
        List<FunctionChangeRow> changesInLeftOverSnapshot = changesInSnapshots.get(leftOverSnapshotDateValue);
        for (FunctionChangeRow r : changesInLeftOverSnapshot) {
            final FunctionIdWithCommit idWithCommit;
            switch (r.modType) {
                case MOVE:
                    idWithCommit = new FunctionIdWithCommit(r.newFunctionId.get(), r.commit, true);
                    break;
                case ADD:
                case MOD:
                    idWithCommit = new FunctionIdWithCommit(r.functionId, r.commit, false);
                    break;
                default:
                    continue;
            }
            leftOverFunctionIdsWithCommits.add(idWithCommit);
        }
        return leftOverFunctionIdsWithCommits;
    }

    private Set<FunctionIdWithCommit> getFunctionIdsWithCommitFromRegularSnapshots() {
        Set<FunctionIdWithCommit> allFunctionsEver = new LinkedHashSet<>();
        for (Map.Entry<Date, List<AllFunctionsRow>> snapshotEntry : allFunctionsInSnapshots.entrySet()) {
            final Date snapshotDate = snapshotEntry.getKey();
            final Snapshot snapshot = projectInfo.getSnapshots().get(snapshotDate);
            final String startHash = snapshot.getStartHash();
            Commit startCommit = commitsDistanceDb.internCommit(startHash);
            for (AllFunctionsRow row : snapshotEntry.getValue()) {
                FunctionIdWithCommit fidWithCommit = new FunctionIdWithCommit(row.functionId, startCommit, false);
                allFunctionsEver.add(fidWithCommit);
            }
        }
        return allFunctionsEver;
    }

    private List<List<FunctionIdWithCommit>> computeFunctionGenealogies(Set<FunctionIdWithCommit> allFunctionsEver, Set<FunctionIdWithCommit> leftOverFunctionIdsWithCommits, Set<FunctionIdWithCommit> functionsAddedInBetween) {
        LOG.info("Computing genealogies of all functions ...");
        Set<FunctionIdWithCommit> functionsToAnalyze = new HashSet<>(leftOverFunctionIdsWithCommits);
        functionsToAnalyze.addAll(allFunctionsEver);
        functionsToAnalyze.addAll(functionsAddedInBetween);
        List<List<FunctionIdWithCommit>> genealogies = moveResolver.computeFunctionGenealogies(functionsToAnalyze);

        Set<FunctionIdWithCommit> functionsToRemove = getFunctionIdWithCommitsToRemoveFromGenealogies(
                allFunctionsEver, leftOverFunctionIdsWithCommits, functionsAddedInBetween);

        for (Iterator<List<FunctionIdWithCommit>> genealogyIt = genealogies.iterator(); genealogyIt.hasNext(); ) {
            List<FunctionIdWithCommit> genealogy = genealogyIt.next();
//            boolean isMatchingGenealogy = false;
//            for (FunctionIdWithCommit id : genealogy) {
//                if (id.commit.commitHash.equals("d620d4368a7ee17d60f2b381d4c80b22c68ba8e2")) {
//                    isMatchingGenealogy = true;
//                    break;
//                }
//            }


            genealogy.removeAll(functionsToRemove);
            if (genealogy.isEmpty()) {
                genealogyIt.remove();
//                if (isMatchingGenealogy) {
//                    LOG.info("Removed genealogy with commit hash d620d4368a7ee17d60f2b381d4c80b22c68ba8e2");
//                }
            } //else {
//                if (isMatchingGenealogy) {
//                    LOG.info("Did not remove genealogy with commit hash d620d4368a7ee17d60f2b381d4c80b22c68ba8e2");
//                }
//            }
        }

//        for (List<FunctionIdWithCommit> genealogy : genealogies) {
//            for (FunctionIdWithCommit id : genealogy) {
//                if (id.commit.commitHash.equals("d620d4368a7ee17d60f2b381d4c80b22c68ba8e2")) {
//                    LOG.info("ID " + id);
//                    break;
//                }
//            }
//        }

        LOG.info("Done computing genealogies of all functions");
        //reportFunctionGenealogies(genealogies);
        return genealogies;
    }

    private Set<FunctionIdWithCommit> getFunctionIdWithCommitsToRemoveFromGenealogies(Set<FunctionIdWithCommit> allFunctionsEver, Set<FunctionIdWithCommit> leftOverFunctionIdsWithCommits, Set<FunctionIdWithCommit> functionsAddedInBetween) {
        Set<FunctionIdWithCommit> functionsToRemove = new HashSet<>();

        functionsToRemove.addAll(functionsAddedInBetween);
        functionsToRemove.addAll(leftOverFunctionIdsWithCommits);
        functionsToRemove.removeAll(allFunctionsEver);

        return functionsToRemove;
    }

    private void reportFunctionGenealogies(List<List<FunctionIdWithCommit>> genealogies) {
        LOG.info("Found " + genealogies.size() + " distinct function genealogies:");
        double[] sizes = new double[genealogies.size()];
        for (int ixGenealogy = 0; ixGenealogy < genealogies.size(); ixGenealogy++) {
            List<FunctionIdWithCommit> genealogy = genealogies.get(ixGenealogy);
            if (LOG.isDebugEnabled()) {
                reportFunctionGenealogy(ixGenealogy, genealogy);
            }
            sizes[ixGenealogy] = genealogy.size();
        }

        Arrays.sort(sizes);

        int minSize = (int) new Min().evaluate(sizes);
        int maxSize = (int) new Max().evaluate(sizes);
        double meanSize = new Mean().evaluate(sizes);
        double medianSize = new Median().evaluate(sizes);

        LOG.info("Number of genealogies: " + genealogies.size());
        LOG.info("min/max/mean/median size of genealogies: " + minSize + "/" + maxSize + "/" + meanSize + "/" + medianSize);
    }

    static void reportFunctionGenealogy(int ixGenealogy, Collection<FunctionIdWithCommit> genealogy) {
        //Set<FunctionId> distinctIds = new HashSet<>();

        LOG.info("Genealogy " + ixGenealogy + ":");

        //StringBuilder sb = new StringBuilder();
        Iterator<FunctionIdWithCommit> it = genealogy.iterator();
        FunctionIdWithCommit first = it.next();
        //sb.append(first.functionId);
        LOG.info("  -> " + first.functionId + " @ " + first.commit);

        //distinctIds.add(first.functionId);

        while (it.hasNext()) {
            FunctionIdWithCommit next = it.next();
            //sb.append(" -> ").append(id);
            //distinctIds.add(id);
            LOG.info("  -> " + next.functionId + " @ " + next.commit);
        }
        //LOG.info(ixGenealogy + " (" + distinctIds.size() + " ID(s)): " + sb.toString());
    }

    static class CommitWindow {
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
        LinkedGroupingListMap<Integer, Snapshot> snapshotsByBranch = new LinkedGroupingListMap<>();
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

            String startHashOfFirstContainingSnapshot = function.getFirstSnapshotCommit();
            Commit startCommitOfFirstContainingSnaphsot = commitsDistanceDb.internCommit(startHashOfFirstContainingSnapshot);

            AgeAndDistance distanceStrings = getAgeAndDistance(functionId, startCommitOfFirstContainingSnaphsot);

            final Set<Commit> allDirectCommitIds = moveResolver.getCommitIdsOfChangesToThisFunction(functionId);
            final Set<Commit> directCommitIdsInWindow = new HashSet<>(allDirectCommitIds);
            directCommitIdsInWindow.retainAll(window.commits);
            FunctionFuture future = moveResolver.getFunctionFuture(functionId, directCommitIdsInWindow);
            Set<FunctionChangeRow> changesToFunctionAndAliasesInSnapshot = future.getChangesFilteredByCommitIds(window.commits);
            AggregatedFunctionChangeStats changeStats = AggregatedFunctionChangeStats.fromChanges(changesToFunctionAndAliasesInSnapshot);

            try {
                csv.printRecord(functionId.signature, functionId.file, distanceStrings.ageString, distanceStrings.distanceString,
                        changeStats.numCommits, changeStats.linesAdded, changeStats.linesDeleted, changeStats.linesChanged);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }

    private AgeAndDistance getAgeAndDistance(FunctionId functionId, Commit startCommitOfFirstContainingSnaphsot) {
        FunctionHistory history = moveResolver.getFunctionHistory(functionId);
        history.setAgeRequestStats(ageRequestStats);
        if (history.knownAddsForFunction.isEmpty()) {
            ageRequestStats.increaseFunctionsWithoutAnyKnownAddingCommits(functionId);
        }
        return AgeAndDistance.fromHistoryAndCommit(history, startCommitOfFirstContainingSnaphsot);
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


    private Map<Date, List<FunctionChangeRow>> readChangesInSnapshots(Collection<Date> snapshotsToProcesses) {
        LOG.debug("Reading function changes for " + snapshotsToProcesses.size() + " snapshot(s).");
        int numChanges = 0;
        FunctionChangeHunksCsvReader reader = new FunctionChangeHunksCsvReader(commitsDistanceDb);
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
        AllFunctionsCsvReader reader = new AllFunctionsCsvReader();
        Map<Date, List<AllFunctionsRow>> result = new LinkedHashMap<>();
        for (Date snapshotDate : snapshotsToProcesses) {
            List<AllFunctionsRow> functions = reader.readFile(config, snapshotDate);
            result.put(snapshotDate, functions);
            rememberFunctionsKnownToExistAt(snapshotDate, functions);
            numFunctionDefinitions += functions.size();
        }
        LOG.info("Read " + numFunctionDefinitions + " function definitions(s).");
        return result;
    }

    private Map<Date, List<AbResRow>> readAllAbResInSnapshots(Collection<Date> snapshotsToProcesses) {
        LOG.debug("Reading annotation data of functions defined in " + snapshotsToProcesses.size() + " snapshot(s).");
        int numFunctionDefinitions = 0;
        AbResCsvReader reader = new AbResCsvReader();
        Map<Date, List<AbResRow>> result = new LinkedHashMap<>();
        for (Date snapshotDate : snapshotsToProcesses) {
            List<AbResRow> functions = reader.readFile(config, snapshotDate);
            result.put(snapshotDate, functions);
            numFunctionDefinitions += functions.size();
        }
        LOG.info("Read annotation data for " + numFunctionDefinitions + " function(s).");
        return result;
    }

    private void rememberFunctionsKnownToExistAt(Date snapshotDate, List<AllFunctionsRow> functions) {
        SortedMap<Date, Snapshot> snapshots = projectInfo.getSnapshots();
        Snapshot snapshot = snapshots.get(snapshotDate);
        String startHash = snapshot.getStartHash();
        Commit startCommit = commitsDistanceDb.internCommit(startHash);
        for (AllFunctionsRow row : functions) {
            moveResolver.putFunctionKnownToExistAt(row.functionId, startCommit);
//            if (startHash.equalsIgnoreCase("0098ed12c242cabb34646a4453f2c1b012c919c7")) {
//                LOG.warn("XXX Exists at: " + startHash + "," + row.functionId);
//            }
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
