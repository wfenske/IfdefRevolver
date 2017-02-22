package de.ovgu.skunk.bugs.correlate.main;

import com.opencsv.CSVReader;
import de.ovgu.skunk.bugs.correlate.data.*;
import de.ovgu.skunk.bugs.correlate.input.CSVHelper;
import de.ovgu.skunk.bugs.correlate.output.PreprocessOutput;
import de.ovgu.skunk.bugs.correlate.processing.Evaluation;
import de.ovgu.skunk.bugs.correlate.processing.Preprocessing;
import de.ovgu.skunk.bugs.createsnapshots.input.FileFinder;
import de.ovgu.skunk.bugs.createsnapshots.main.CreateSnapshots;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class Correlate {

    private static Logger log = Logger.getLogger(Correlate.class);

    /**
     * The main method.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Correlate self = new Correlate();
        self.run(args);
    }

    private Config conf;

    public Correlate() {
        this.conf = new Config();
    }

    public void run(String args[]) {
        // gather input
        try {
            this.conf = parseCommandLineArgs(args);
        } catch (Exception e) {
            System.err.println("Error while processing command line arguments: " + e);
            e.printStackTrace();
            System.err.flush();
            System.out.flush();
            System.exit(1);
        }

        deleteOutputFiles();

        final CSVHelper csvHelper = new CSVHelper(conf);
        csvHelper.readSnapshotsAndRevisionsFile();

		/* PREPROCESSING of the Smell Data */

        for (Smell smell : Smell.values()) {
            (new Preprocessing(conf, csvHelper)).preprocessDataForSmell(smell);
        }

		/* PREPROCESSING of the Project Data */

        // List<Date> snapshotDates = csvHelper.getProjectDates();
        final Collection<Snapshot> snapshots = csvHelper.getSnapshots().values();

        final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
        logSnapshotDates(snapshots);

        // Map<Snapshot, SortedMap<ChangedFile, String>> groupedBugFixCounts =
        // groupBySnapshots(allBugFixCounts,
        // snapshots);
        // Map<Snapshot, SortedMap<ChangedFile, String>> groupedChangeCounts =
        // groupBySnapshots(allChangeCounts,
        // snapshots);

        // Date startDate = versionDates.get(0);

        for (Snapshot snapshot : snapshots) {
            log.info("Evaluating snapshot: " + snapshot);

            List<MergedFileInfo> outputList = new ArrayList<>();

            SortedMap<FileChangeHunk, String> bugFixesInSnapshot = csvHelper
                    .getFixedFiles(snapshot);
            SortedMap<FileChangeHunk, String> changesInSnapshot = csvHelper
                    .getChangedFiles(snapshot);

            // Vorbereitung für MergedFileInfos
            Map<String, Integer> curBugFixCounts = countOccurrencesOfSameName(bugFixesInSnapshot);
            Map<String, Integer> curChangeCounts = countOccurrencesOfSameName(changesInSnapshot);

            // TODO: Vorbereitung für einzelne Commits (nur für die einzelnen
            // Ratios pro Commit nötig... wahrscheinlich wieder zu entfernen)
            // Map<String, Integer> curBugSetSingle =
            // Preprocessing.getCurFiles(bugMapSingle, startDate, curDate);
            // Map<String, Integer> curChangedSetSingle =
            // Preprocessing.getCurFiles(changedMapSingle, startDate, curDate);
            ////////////////////////////////////////////////

            Map<Smell, Map<String, List<Double>>> allSmells = new HashMap<>();

            for (Smell smell : Smell.values()) {
                Map<String, List<Double>> smellScores = csvHelper.getSmells(snapshot, smell);
                allSmells.put(smell, smellScores);
            }

            Set<String> filesInSnapshot = csvHelper.getFilesInSnapshot(snapshot);

            final String snapshotDateString = dateFormatter.format(snapshot.getSnapshotDate());
            for (String sourceFileName : filesInSnapshot) {
                MergedFileInfo fileInfo = new MergedFileInfo(sourceFileName,
                        snapshot.getSnapshotDate());

                Path snapshotsPath = conf.projectSnapshotsDir().toPath();
                Path filePath = snapshotsPath
                        .resolve(Paths.get(snapshotDateString, "_cppstats", sourceFileName));
                File file = filePath.toFile();
                String baseName = file.getName();

                // Note: Simply counting file lines actually does give us a good
                // SLOC (source lines of code) measurement since we do it on
                // files prepared by cppstats, which removes comments, empty
                // lines, etc..
                int lines;
                try {
                    lines = countLines(file);
                } catch (IOException e) {
                    throw new RuntimeException(
                            "Error counting lines of code in file " + file.getAbsolutePath(), e);
                }
                fileInfo.setSourceLinesOfCode(lines);

                final Integer fixCount = curBugFixCounts.get(baseName);
                if (fixCount != null) {
                    fileInfo.setFixCount(fixCount);
                }

                final Integer changeCount = curChangeCounts.get(baseName);
                if (changeCount != null) {
                    fileInfo.setChangeCount(changeCount);
                }

                for (Smell smell : Smell.values()) {
                    Map<String, List<Double>> allScoresForSmell = allSmells.get(smell);
                    List<Double> scoresForFile = allScoresForSmell.get(sourceFileName);
                    if (scoresForFile != null) {
                        fileInfo.addSmells(smell, scoresForFile);
                    }
                }

                outputList.add(fileInfo);
            }

            // Output

            // @formatter:off
            // if (false) {
            // Vorbereitung für Proportion Test
            // Map<ChangedFile, String> curBugMap = new HashMap<>();
            // for (ChangedFile f : bugFixesInSnapshot.keySet()) {
            // curBugMap.put(f, f.getName());
            // }
            // writeCorrelatedRatioCsv(startDate, curBugMap,
            // curVersionSmellyFiles, versionFiles, dateFormatter);
            // }
            // @formatter:on

            PreprocessOutput.writeCorrelationForSnapshot(outputList,
                    dateFormatter.format(snapshot.getSnapshotDate()), conf);
        }

        evalAllSnapshots();
    }

    /**
     * <p>
     * Count lines in a file, similar (but not identical) to what wc -l does
     * </p>
     * <p>
     * <p>
     * Taken from <a href=
     * "http://stackoverflow.com/questions/453018/number-of-lines-in-a-file-in-java">
     * Stackoverflow</a>
     * </p>
     *
     * @param file
     * @return
     * @throws IOException If something goes wrong while reading the file
     */
    private static int countLines(File file) throws IOException {
        InputStream is = new BufferedInputStream(new FileInputStream(file));
        try {
            byte[] c = new byte[1024];
            int count = 0;
            int readChars = 0;
            boolean empty = true;
            while ((readChars = is.read(c)) != -1) {
                empty = false;
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n') {
                        ++count;
                    }
                }
            }
            return (count == 0 && !empty) ? 1 : count;
        } finally {
            is.close();
        }
    }

    // @formatter:off
    /*
     * private Map<Snapshot, SortedMap<ChangedFile, String>>
	 * groupBySnapshots(SortedMap<ChangedFile, String> changedFiles,
	 * Collection<Snapshot> snapshots) throws AssertionError { Map<Snapshot,
	 * SortedMap<ChangedFile, String>> result = new HashMap<>();
	 * 
	 * SortedMap<ChangedFile, String> work = new TreeMap<>(changedFiles);
	 * 
	 * for (Snapshot snapshot : snapshots) { SortedMap<ChangedFile, String>
	 * mapForSnapshot = new TreeMap<>(); while (!work.isEmpty()) { ChangedFile
	 * changedFile = work.firstKey(); Date fileDate =
	 * changedFile.getCommitDate(); if (fileDate.after(snapshotDate)) { break; }
	 * String value = work.get(changedFile); mapForSnapshot.put(changedFile,
	 * value); work.remove(changedFile); } result.put(snapshotDate,
	 * mapForSnapshot); }
	 * 
	 * if (log.isInfoEnabled() && !work.isEmpty()) { Set<String> uniqueHashes =
	 * new HashSet<>(); for (ChangedFile f : work.keySet()) {
	 * uniqueHashes.add(f.getCommitHash()); } log.info("After grouping, " +
	 * work.size() + " file(s), with " + uniqueHashes.size() +
	 * " hash(es), remain(s):  " + work.keySet()); }
	 * 
	 * return result; }
	 */
    // @formatter:on

    /**
     * @param changedFiles Map mit Dateien im ChangedFile-Objektformat
     * @return Anzahl der Vorkommnisse für ein und denselben Dateinamen
     */
    private static Map<String, Integer> countOccurrencesOfSameName(
            SortedMap<FileChangeHunk, String> changedFiles) {
        Set<FileCommit> uniqueFileCommits = new HashSet<>();
        for (FileChangeHunk changedFile : changedFiles.keySet()) {
            uniqueFileCommits.add(changedFile.getFileCommit());
        }

        Map<String, Integer> result = new HashMap<>();
        // for (FileChangeHunk changedFile : changedFiles.keySet()) {
        for (FileCommit changedFile : uniqueFileCommits) {
            // curBugSet.add(bugMap.get(keySec));

            String fileName = changedFile.getFilename();
            if (result.containsKey(fileName)) {
                int counter = result.get(fileName);
                result.put(fileName, ++counter);
            } else {
                result.put(fileName, 1);
            }
        }

        return result;
    }

    private void logSnapshotDates(Collection<Snapshot> snapshots) {
        StringBuilder snapshotDatesPrettyPrinted = new StringBuilder();
        for (Snapshot snapshot : snapshots) {
            snapshotDatesPrettyPrinted.append("\n\t");
            snapshotDatesPrettyPrinted.append(snapshot.toString());
        }
        log.info("Evaluating snapshots: " + snapshotDatesPrettyPrinted);
    }

    @SuppressWarnings("unused")
    private void writeCorrelatedRatioCsv(Date startDate, Map<FileChangeHunk, String> curBugMap,
                                         Set<String> curVersionSmellyFiles, Set<String> versionFiles,
                                         final SimpleDateFormat formatter) {
        String dateStr = formatter.format(startDate);
        /*
         * ------------------------------------------------------------- ----
		 * ----
		 */
        // Aktuelle Commitfile Map in Liste umwandeln
        List<CommitFile> listOfBugcommits = new ArrayList<>();
        for (FileChangeHunk chFile : curBugMap.keySet()) {
            String fileName = chFile.getFilename();
            boolean smelly = curVersionSmellyFiles.contains(fileName);
            CommitFile comFile = new CommitFile(fileName, smelly);
            listOfBugcommits.add(comFile);
        }
        int curSnapshotSizeDebug = curBugMap.size();
        int curSnapshotSize = listOfBugcommits.size();

        // nur für die einzelnen Ratios pro Commit nötig...
        // wahrscheinlich wieder zu entfernen
        File corrRatioDir = new File(conf.projectResultsDir(), "CorrelatedRatio");
        corrRatioDir.mkdirs();
        File corrRatioCsvOut = new File(corrRatioDir, dateStr + "_ratio.csv");

        if (corrRatioCsvOut.exists()) {
            throw new RuntimeException(corrRatioCsvOut.getAbsolutePath() + " already exists!");
        }

        BufferedWriter buffW = null;
        int smellyFixAmount = 0;
        int nonSmellyFixAmount = 0;
        if (curSnapshotSize <= 1) {
            smellyFixAmount = 0;
            nonSmellyFixAmount = 0;
        } else {
            int i = 0;
            while (i <= 40) {
                int randomNum = randInt(0, curSnapshotSize - 1);
                log.info(listOfBugcommits.get(randomNum).getFile());
                if (versionFiles.contains(listOfBugcommits.get(randomNum).getFile())) {
                    if (listOfBugcommits.get(randomNum).getSmelly()) {
                        smellyFixAmount++;
                    } else {
                        nonSmellyFixAmount++;
                    }
                    i++;
                }
            }
        }

		/*
         * ------------------------------------------------------------- ----
		 * ----
		 */

        try {
            buffW = new BufferedWriter(new FileWriter(corrRatioCsvOut, true));
            buffW.write(smellyFixAmount + "," + nonSmellyFixAmount + "," + curSnapshotSize);
            buffW.newLine();
            buffW.flush();
            buffW.close();
        } catch (IOException e) {
            throw new RuntimeException("Error writing file " + corrRatioCsvOut, e);
        }
    }

    private void deleteOutputFiles() {
        for (Smell smell : Smell.values()) {
            File f = conf.smellOverviewFile(smell);
            deleteFileIfExistsOrDie(f);
        }

        deleteFileIfExistsOrDie(conf.corOverviewFile());
        deleteFileIfExistsOrDie(conf.corOverviewSizeFile());
    }

    private void deleteFileIfExistsOrDie(File f) {
        if (f.exists()) {
            log.warn("Output file already exists and will be deleted: " + f.getAbsolutePath());
            if (!f.delete()) {
                throw new RuntimeException(
                        "Could not delete pre-existing output file " + f.getAbsolutePath());
            }
        }
    }

    private void evalAllSnapshots() {
        File pathFind = conf.correlatedResultsDir();
        log.info("Aggregating correlation results in snapshot directory: "
                + pathFind.getAbsolutePath());

        // Create the output files
        File corOverviewFile = createCorOverviewCsv(conf.corOverviewFile());
        File corOverviewSizeFile = createCorOverviewCsv(conf.corOverviewSizeFile());

        List<File> filesFound = FileFinder.find(pathFind, "(.*\\.csv$)");

        Collections.sort(filesFound);

        // Extend the output files, snapshot by snapshot
        for (File snapshotCorrelatedData : filesFound) {
            Evaluation.evalFile(snapshotCorrelatedData, 0, corOverviewFile);
            int minFileSize = computeMinFileSizeForLargeFiles(snapshotCorrelatedData);
            Evaluation.evalFile(snapshotCorrelatedData, minFileSize, corOverviewSizeFile);
        }

        log.info("Successfully wrote correlation data to " + conf.corOverviewFile() + " and "
                + conf.corOverviewSizeFile());
    }

    private int computeMinFileSizeForLargeFiles(File snapshotCorrelatedData) {
        List<Integer> sizeList = readFileSizes(snapshotCorrelatedData);

        final int lenSizeList = sizeList.size();

        if (lenSizeList > 0) {
            Collections.sort(sizeList);
            final double percentage = conf.getLargeFileSizePercentage();

            if (percentage < 0.0 || percentage > 100.0) {
                throw new IllegalArgumentException(
                        "Size percentage value must lie between 0.0 and 100.0. Got " + percentage);
            }

            int ixMinSize = (int) Math.round(lenSizeList - lenSizeList * percentage / 100.0);
            ixMinSize = Math.max(0, Math.min(ixMinSize, lenSizeList - 1));
            int minFileSize = Math.round(sizeList.get(ixMinSize));
            return minFileSize;
        } else {
            return 0;
        }
    }

    private List<Integer> readFileSizes(File snapshotCorrelatedData) {
        List<Integer> sizeList = new ArrayList<>();
        CSVReader reader = null;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(snapshotCorrelatedData);
            reader = new CSVReader(fileReader);
            String[] nextLine;
            reader.readNext(); // Skip header
            while ((nextLine = reader.readNext()) != null) {
                int fileSize = (Integer) SnapshotCorrelationCsvColumn.SLOC.parseFromCsv(nextLine);
                sizeList.add(fileSize);
            }
        } catch (IOException e1) {
            throw new RuntimeException(
                    "Error reading file " + snapshotCorrelatedData.getAbsolutePath(), e1);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // Could not close reader. So what?
                }
            } else if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    // Could not close reader. So what?
                }
            }
        }
        return sizeList;
    }

    private File createCorOverviewCsv(File csvOut) {
        if (csvOut.exists()) {
            throw new RuntimeException(csvOut.getAbsolutePath() + " already exists!");
        }

        String header = Evaluation.getCorOverviewCsvHeader();

        BufferedWriter buff = null;
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(csvOut);
            buff = new BufferedWriter(fileWriter);
            buff.write(header);
            buff.newLine();
            buff.close();
        } catch (IOException e) {
            throw new RuntimeException("Error writing file " + csvOut.getAbsolutePath(), e);
        } finally {
            if (buff != null) {
                try {
                    buff.close();
                } catch (IOException e) {
                    // Don't care
                }
            } else if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    // Don't care
                }
            }
        }

        return csvOut;
    }

    /**
     * Random Int
     */
    private static int randInt(int min, int max) {
        Random rand = new Random();
        int randomNum = 0;

        randomNum = rand.nextInt((max - min) + 1) + min;

        return randomNum;
    }

    /**
     * Large file evaluation: percentage of files (LOC-wise) to be considered
     * large
     *
     * @see Config#getLargeFileSizePercentage()
     */
    private static final String OPT_LARGE_FILE_PERCENT_PERCENT_L = "large-file-p";

    /**
     * Large feature detection: LOC (lines of code) percent value
     *
     * @see Config#getLargeFeatureLocPercentage()
     */
    private static final String OPT_LARGE_FEATURE_LOC_PERCENT_L = "lf-loc-p";

    /**
     * Large feature detection: NOFL (number of feature locations) percent value
     *
     * @see Config#getLargeFeatureOccurrencePercentage()
     */
    private static final String OPT_LARGE_FEATURE_NOFL_PERCENT_L = "lf-nofl-p";

    /**
     * Large feature detection: NOCU (number of compilation units) percent value
     *
     * @see Config#getLargeFeatureNumCompilationUnitsPercentage()
     */
    private static final String OPT_LARGE_FEATURE_NOCU_PERCENT_L = "lf-nocu-p";

    /**
     * Analyze input to decide what to do during runtime
     *
     * @param args the command line arguments
     */
    private Config parseCommandLineArgs(String[] args) {

        CommandLineParser parser = new DefaultParser();
        Options fakeOptionsForHelp = makeOptions(true);
        Options actualOptions = makeOptions(false);

        CommandLine line;
        try {
            CommandLine dummyLine = parser.parse(fakeOptionsForHelp, args);
            if (dummyLine.hasOption('h')) {
                System.out.flush();
                System.err.flush();
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp(progName() + " [OPTIONS]",
                        "Correlate change and bug-fix information for VCS repository snapshots created by " +
                                CreateSnapshots.class.getSimpleName() +
                                ".\n\nOptions:\n",
                        actualOptions, null, false);
                System.out.flush();
                System.err.flush();
                System.exit(0);
                // We never actually get here due to the preceding
                // System.exit(int) call.
                return null;
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
            return null;
        }

        Config result = new Config();

        ProjectInformationConfig.parseProjectNameFromCommandLine(line, result);

        ProjectInformationConfig.parseProjectResultsDirFromCommandLine(line, result);

        ProjectInformationConfig.parseSnapshotsDirFromCommandLine(line, result);

        // Large file percentage
        Optional<Double> largeFileSizePercentage = getPercentOptionValue(line,
                OPT_LARGE_FILE_PERCENT_PERCENT_L);
        if (largeFileSizePercentage.isPresent()) {
            result.largeFileSizePercentage = largeFileSizePercentage.get();
        }

        // Large feature percentages
        Optional<Double> largeFeatureLocPercentage = getPercentOptionValue(line,
                OPT_LARGE_FEATURE_LOC_PERCENT_L);
        if (largeFeatureLocPercentage.isPresent()) {
            result.largeFeatureLocPercentage = largeFeatureLocPercentage.get();
        }
        Optional<Double> largeFeatureOccurrencePercentage = getPercentOptionValue(line,
                OPT_LARGE_FEATURE_NOFL_PERCENT_L);
        if (largeFeatureOccurrencePercentage.isPresent()) {
            result.largeFeatureOccurrencePercentage = largeFeatureOccurrencePercentage.get();
        }
        Optional<Double> largeFeatureNumCompilationUnitsPercentage = getPercentOptionValue(line,
                OPT_LARGE_FEATURE_NOCU_PERCENT_L);
        if (largeFeatureNumCompilationUnitsPercentage.isPresent()) {
            result.largeFeatureNumCompilationUnitsPercentage = largeFeatureNumCompilationUnitsPercentage
                    .get();
        }

        return result;
    }

    private Optional<Double> getPercentOptionValue(CommandLine line, String optionName) {
        if (!line.hasOption(optionName)) {
            return Optional.empty();
        }
        String stringVal = line.getOptionValue(optionName);
        double dVal;
        try {
            dVal = Double.parseDouble(stringVal);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Illegal value for for option `" + optionName
                    + "'. Not a parsable double value: " + stringVal);
        }
        if (dVal < 0.0 || dVal > 100.0) {
            throw new IllegalArgumentException("Invalid percent value for option `" + optionName
                    + "'. Expected 0.0 <= value <= 100.0, got " + dVal);
        }
        return Optional.of(dVal);
    }

    private Options makeOptions(boolean forHelp) {
        boolean required = !forHelp;
        final Config defaultConf = new Config();

        Options options = new Options();
        // @formatter:off

        // --help= option
        options.addOption(ProjectInformationConfig.helpCommandLineOption());

        // Options for describing project locations
        options.addOption(ProjectInformationConfig.projectNameCommandLineOption(required));
        options.addOption(ProjectInformationConfig.resultsDirCommandLineOption());
        options.addOption(ProjectInformationConfig.snapshotsDirCommandLineOption());

        // Option for large file evaluation
        options.addOption(Option.builder().longOpt(OPT_LARGE_FILE_PERCENT_PERCENT_L)
                .desc("Large file evaluation: The top-x percent of files, LOC-wise,"
                        + " are considered large files. Values must be >= 0.0 and <= 100.0." + String
                        .format(Locale.ENGLISH, " [Default=%.1f]", defaultConf.getLargeFileSizePercentage()))
                .hasArg().argName("PERCENT").build());

        // Options for large feature detection
        options.addOption(Option.builder().longOpt(OPT_LARGE_FEATURE_LOC_PERCENT_L)
                .desc("Large Feature detection: The top-x percent of features, LOC-wise,"
                        + " are considered Large Features. Values must be >= 0.0 and <= 100.0."
                        + String.format(Locale.ENGLISH, " [Default=%.1f]", defaultConf.getLargeFeatureLocPercentage()))
                .hasArg().argName("PERCENT").build());
        options.addOption(Option.builder().longOpt(OPT_LARGE_FEATURE_NOFL_PERCENT_L)
                .desc("Large Feature detection: The top-x percent of features, regarding references in #ifdefs,"
                        + " are considered Large Features. Values must be >= 0.0 and <= 100.0."
                        + String.format(Locale.ENGLISH, " [Default=%.1f]",
                        defaultConf.getLargeFeatureOccurrencePercentage()))
                .hasArg().argName("PERCENT").build());
        options.addOption(Option.builder().longOpt(OPT_LARGE_FEATURE_NOCU_PERCENT_L)
                .desc("Large Feature detection: The top-x percent of features, regarding the number of compilation units in which they occur,"
                        + " are considered Large Features. Values must be >= 0.0 and <= 100.0."
                        + String.format(Locale.ENGLISH, " [Default=%.1f]",
                        defaultConf.getLargeFeatureNumCompilationUnitsPercentage()))
                .hasArg().argName("PERCENT").build());

        // @formatter:on
        return options;
    }

    private String progName() {
        return this.getClass().getSimpleName();
    }
}
