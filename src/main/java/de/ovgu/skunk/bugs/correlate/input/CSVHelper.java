package de.ovgu.skunk.bugs.correlate.input;

import com.opencsv.CSVReader;
import de.ovgu.skunk.bugs.correlate.data.Feature;
import de.ovgu.skunk.bugs.correlate.data.FileChangeHunk;
import de.ovgu.skunk.bugs.correlate.data.Snapshot;
import de.ovgu.skunk.bugs.correlate.main.Config;
import de.ovgu.skunk.bugs.correlate.main.Smell;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

public class CSVHelper {
    private static Logger log = Logger.getLogger(CSVHelper.class);

	/* Indices into Skunk's feature location CSV file */
    /**
     * Column LGSmell (smell score assigned by Skunk)
     */
    private static final int SFL_IX_LGSMELL = 1;
    /**
     * Column NOFC (number of feature constants)
     */
    private static final int SFL_IX_NOFC = 6;
    /**
     * Column LOFC (lines of feature code)
     */
    private static final int SFL_IX_LOFC = 8;
    /**
     * Column NOCU (number of compilation units)
     */
    private static final int SFL_IX_NOCU = 10;

    // Liste für geänderte Dateien
    // private SortedMap<ChangedFile, String> changedFiles = new
    // TreeMap<ChangedFile, String>();
    // Liste für jeden x-ten Bugfix Commit
    // private SortedMap<ChangedFile, String> bugFiles = new
    // TreeMap<ChangedFile, String>();

    // Liste für geänderte Dateien
    // private SortedMap<ChangedFile, String> changedFilesSingle = new
    // TreeMap<ChangedFile, String>();
    // Liste für jeden x-ten Bugfix Commit
    // private SortedMap<ChangedFile, String> bugFilesSingle = new
    // TreeMap<ChangedFile, String>();
    private final Config conf;

    private SortedMap<Date, Snapshot> snapshots;

    private SortedMap<Snapshot, SortedMap<FileChangeHunk, String>> changedFilesBySnapshot = new TreeMap<>();
    private SortedMap<Snapshot, SortedMap<FileChangeHunk, String>> fixedFilesBySnapshot = new TreeMap<>();

    /**
     * Instantiates a new CSVReader
     *
     * @param detPath the path of the bugdetection csv
     */
    public CSVHelper(Config conf) {
        this.conf = conf;
    }

    /**
     * @param smell
     * @param snapshotSmellFile
     * @return Names of files that exhibit the smells listed in
     * <code>snapshotSmellFile</code>
     */
    public Map<String, List<Double>> getSmellyFilesFromSnapshotSmellResFile(Smell smell,
                                                                            final File snapshotSmellFile) {
        List<Double> scoreList = new ArrayList<>();
        Map<String, List<Double>> scoresByFilename = new HashMap<>();
        CSVReader reader = null;
        try {
            reader = new CSVReader(new FileReader(snapshotSmellFile));
            @SuppressWarnings("unused")
            String[] header = reader.readNext(); // erste Zeile überspringen
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                String rawFileName = nextLine[0];
                // int startingLine = Integer.parseInt(nextLine[1]);
                // String methodName = nextLine[2];
                final double smellScore;
                if (smell == Smell.AB) {
                    smellScore = Double.parseDouble(nextLine[3]);
                } else {
                    smellScore = Double.parseDouble(nextLine[1]);
                }

                if (smellScore >= conf.getSmellScoreThreshold()) {
                    final String filename = parseFilename(rawFileName);

                    List<Double> scoresForFile = ensureScoreListForKey(scoresByFilename, filename);

                    scoresForFile.add(smellScore);
                    scoreList.add(smellScore);
                    // @formatter:off
                    /*
					if (!scoresByFilename.containsKey(fileName)) {
						scoresByFilename.put(fileName, smellScore);
						scoreList.add(smellScore);
					}
					*/
                    // @formatter:on
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading file " + snapshotSmellFile.getAbsolutePath(),
                    e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // Could not close reader. So what?
                }
            }
        }

        // For Large Feature
        if (smell == Smell.LF) {
            // No filtering for Large Feature at this point.
            return scoresByFilename;
        } else { // For !Large Feature (all the other smells)
            final double minSmellScore = getMinSmellScoreForUpperPercentile(scoreList,
                    conf.getSmellyFilesFraction());
            for (Iterator<Entry<String, List<Double>>> it = scoresByFilename.entrySet()
                    .iterator(); it.hasNext(); ) {
                Entry<String, List<Double>> entry = it.next();
                List<Double> scores = entry.getValue();
                for (Iterator<Double> itScore = scores.iterator(); itScore.hasNext(); ) {
                    double score = itScore.next();
                    if (score < minSmellScore) {
                        itScore.remove();
                    }
                }

                if (scores.isEmpty()) {
                    it.remove();
                }
            }
            return scoresByFilename;
        }
    }

    private static List<Double> ensureScoreListForKey(Map<String, List<Double>> scoresByFilename,
                                                      String filename) {
        List<Double> value = scoresByFilename.get(filename);
        if (value != null) {
            return value;
        } else {
            value = new LinkedList<>();
            scoresByFilename.put(filename, value);
            return value;
        }
    }

    private static double getMinSmellScoreForUpperPercentile(List<Double> scoreList,
                                                             double percentile) {
        final int len = scoreList.size();
        int percSum = (int) Math.round(len * percentile);

        if (len > 0) {
            final List<Double> sortedScores = new ArrayList<>(scoreList);
            Collections.sort(sortedScores);
            int ixMin = len - percSum;
            ixMin = Math.max(0, Math.min(ixMin, len - 1));
            double minVal = sortedScores.get(ixMin);
            return minVal;
        } else {
            return 0.0;
        }

        // @formatter:off
		/*
		 * int totalSum = 0; log.
		 * warn("This code is inefficient and should be removed. Just return minVal instead."
		 * ); Collections.reverse(scoreList);
		 * 
		 * for (double temp : scoreList) { totalSum++; if (totalSum >= percSum)
		 * { if (temp != minVal) { throw new RuntimeException(
		 * "Got different values for min smell score: " + temp + " vs. " +
		 * minVal + ". ScoreList == " + Arrays.toString(scoreList.toArray()) +
		 * " percentile == " + percentile); } return temp; } }
		 * 
		 * return 0;
		 */
        // @formatter:on
    }

    private String parseFilename(String fileName) {
        // filename muss noch beschnitten werden
        final String suffix = ".xml";
        final Path p = Paths.get(fileName);

        final String cppStatsFolderName = "_cppstats_featurelocations";
        int ixCppstatsDir = -1;
        final int len = p.getNameCount();

        final String basename = p.getName(len - 1).toString();
        final String basenameWithoutSuffix = removeSuffix(basename, suffix);

        for (int i = 0; ixCppstatsDir < len - 1; i++) {
            Path name = p.getName(i);
            if (name.toString().equals(cppStatsFolderName)) {
                ixCppstatsDir = i;
                break;
            }
        }

        if (ixCppstatsDir == -1) {
            throw new RuntimeException("Invalid file name in smell results (does not contain `"
                    + cppStatsFolderName + "'): " + fileName);
        }

        final int ixStart = ixCppstatsDir + 1;
        final int ixEnd = len - 1;

        if (ixStart == ixEnd) {
            return basenameWithoutSuffix;
        } else {
            final Path subdir = p.subpath(ixStart, ixEnd);
            final Path subpath = Paths.get(subdir.toString(), basenameWithoutSuffix);
            return subpath.toString();
        }

        // int fileIdx = fileName.lastIndexOf("locations/") +
        // "locations/".length();

        // Remove XML suffix

        // int lastdotIdx = fileName.lastIndexOf(".");

        // String relativeSourceFilename = fileName.substring(fileIdx,
        // lastdotIdx);
    }

    private String removeSuffix(final String basename, final String suffix) {
        if (!basename.endsWith(suffix)) {
            throw new RuntimeException(
                    "Unexpected filename in smell results file (was supposed to end in `" + suffix
                            + "':" + basename);
        }
        final String basenameWithoutSuffix = basename.substring(0,
                basename.length() - suffix.length());
        return basenameWithoutSuffix;
    }

    /**
     * Main entry point of this class, reads all the necessary project
     * information
     */
    public void processFiles() {
        snapshots = readSnapshots();
        processRevisionsFile();
        // processFileSingle();
    }

    /**
     * Nimmt die ursprüngliche CSV-Datei von MetricMiner2 und erstellt die
     * Listen der Bugfixes und geänderten Dateien mit ihren Änderungsdaten
     */
    private void processRevisionsFile() {
        log.info("Reading revisions file " + conf.getRevisionCsvFile());

        final String cvsSplitBy = ",";
        final DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        final Map<String, Snapshot> snapshotsByCommit = mapSnapshotsToCommits();

        BufferedReader br = null;
        String line = "";

        try {

            br = new BufferedReader(new FileReader(conf.getRevisionCsvFile()));
            while ((line = br.readLine()) != null) {

                // use comma as separator
                String[] modification = line.split(cvsSplitBy);
                String curHash = modification[0];

                Snapshot snapshot = snapshotsByCommit.get(curHash);

                if (snapshot == null) {
                    log.debug("Skipping commit " + curHash + ": not part of any snapshot");
                    continue;
                }

                boolean bugfixCommit = Boolean.parseBoolean(modification[1]);
                // int bugfixCount = Integer.parseInt(commit[8]);
                String strDate = modification[7];
                String fileName = modification[3];

                Date comDate;
                try {
                    comDate = formatter.parse(strDate);
                } catch (ParseException e) {
                    throw new RuntimeException("Could not parse date " + strDate + " in file "
                            + conf.getRevisionCsvFile(), e);
                }

                FileChangeHunk chFile = new FileChangeHunk(fileName, curHash, comDate);

                putChangedFile(this.changedFilesBySnapshot, snapshot, chFile, fileName);

                if (bugfixCommit) {
                    putChangedFile(this.fixedFilesBySnapshot, snapshot, chFile, fileName);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Error reading revisions CSV file " + conf.getRevisionCsvFile(), e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    // Don't care
                }
            }
        }
    }

    private void putChangedFile(SortedMap<Snapshot, SortedMap<FileChangeHunk, String>> map,
                                Snapshot snapshot, FileChangeHunk chFile, String fileName) {
        SortedMap<FileChangeHunk, String> changedFiles = ensureValueForKey(map, snapshot);
        changedFiles.put(chFile, fileName);
    }

    private static SortedMap<FileChangeHunk, String> ensureValueForKey(
            SortedMap<Snapshot, SortedMap<FileChangeHunk, String>> filesBySnapshot,
            Snapshot snapshot) {
        SortedMap<FileChangeHunk, String> value = filesBySnapshot.get(snapshot);
        if (value != null) {
            return value;
        }
        value = new TreeMap<>();
        filesBySnapshot.put(snapshot, value);
        return value;
    }

    private Map<String, Snapshot> mapSnapshotsToCommits() {
        Map<String, Snapshot> snapshotsByCommit = new HashMap<>();
        for (Snapshot s : snapshots.values()) {
            for (String commitHash : s.getCommitHashes()) {
                Snapshot previousSnapshot = snapshotsByCommit.put(commitHash, s);
                if (previousSnapshot != null) {
                    throw new RuntimeException("Commit " + commitHash + " occurs in two snapshots "
                            + previousSnapshot + " and " + s);
                }
            }
        }
        return snapshotsByCommit;
    }

    private SortedMap<Date, Snapshot> readSnapshots() {
        SortedMap<Date, Snapshot> result = new TreeMap<>();
        List<Date> snapshotDates = getProjectDates();

        final DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        File projSnapshotMetadataDir = new File(conf.getResultsDir(), "snapshots");
        for (Date snapshotDate : snapshotDates) {
            File snapshotFile = new File(projSnapshotMetadataDir,
                    formatter.format(snapshotDate) + ".csv");
            Snapshot snapshot = readSnapshot(snapshotFile);
            result.put(snapshotDate, snapshot);
        }

        return result;
    }

    private Snapshot readSnapshot(File snapshotFile) {
        Set<String> commitHashes = new LinkedHashSet<>();
        int snapshotIndex = -1;
        Date snapshotDate = null;
        final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

        CSVReader reader = null;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(snapshotFile);
            reader = new CSVReader(fileReader);
            String[] header = reader.readNext();
            try {
                snapshotIndex = Integer.parseInt(header[0]);
                snapshotDate = dateFormatter.parse(header[1]);
            } catch (ParseException pe) {
                throw new RuntimeException(
                        "Error parsing header of snapshot file " + snapshotFile.getAbsolutePath());
            }
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                String commitHash = nextLine[0];
                commitHashes.add(commitHash);
            }
        } catch (IOException e1) {
            throw new RuntimeException(
                    "Error reading file " + conf.projectAnalysisFile().getAbsolutePath(), e1);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // We don't care if closing the reader fails.
                }
            } else if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    // We don't care if closing the reader fails.
                }
            }
        }

        return new Snapshot(snapshotIndex, snapshotDate, commitHashes);
    }

    // @formatter:off
    /**
     * Nimmt die ursprüngliche CSV-Datei von MetricMiner2 und erstellt die
     * Listen der Bugfixes und geänderten Dateien mit ihren Änderungsdaten
     *
     * @param csvFileName
     */
	/*
	 * private void processFileSingle() { BufferedReader br = null; String line
	 * = ""; String cvsSplitBy = ",";
	 * 
	 * SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
	 * 
	 * try {
	 * 
	 * br = new BufferedReader(new FileReader(conf.getRevisionCsvFile())); while
	 * ((line = br.readLine()) != null) { // use comma as separator String[]
	 * commit = line.split(cvsSplitBy); String curHash = commit[0]; boolean
	 * bugfixCommit = Boolean.parseBoolean(commit[1]); int bugfixCount =
	 * (Integer.parseInt(commit[8]) - 1) % 100; String strDate = commit[7];
	 * String fileName = null; if (bugfixCount < 10) { fileName = commit[3] +
	 * "0" + bugfixCount; } else { fileName = commit[3] + bugfixCount; }
	 * 
	 * Date dateStr; Date comDate = null; try { dateStr =
	 * formatter.parse(strDate); String formattedDate =
	 * formatter.format(dateStr); comDate = formatter.parse(formattedDate); }
	 * catch (ParseException e) { throw new RuntimeException(
	 * "Could not parse date " + strDate + " in file " +
	 * conf.getRevisionCsvFile(), e); }
	 * 
	 * ChangedFile chFile = new ChangedFile(fileName, curHash, comDate);
	 * 
	 * changedFilesSingle.put(chFile, fileName); if (bugfixCommit)
	 * bugFilesSingle.put(chFile, fileName);
	 * 
	 * }
	 * 
	 * } catch (IOException e) { throw new
	 * RuntimeException("Error reading file " + conf.getRevisionCsvFile(), e); }
	 * finally { if (br != null) { try { br.close(); } catch (IOException e) {
	 * // We don't care. } } } }
	 */
    // @formatter:on

    /**
     * @param skunkFeatureLocationsCsv
     * @return Map from feature name to {@link Feature} data structure
     */
    public SortedMap<String, Feature> getFeaturesByName(File skunkFeatureLocationsCsv) {
        SortedMap<String, Feature> featMap = new TreeMap<>();

        CSVReader reader = null;
        try {
            reader = new CSVReader(new FileReader(skunkFeatureLocationsCsv));
            String[] nextLine;
            reader.readNext(); // erste Zeile überspringen
            while ((nextLine = reader.readNext()) != null) {
                String featName = nextLine[0];
                double smellScore = Double.parseDouble(nextLine[SFL_IX_LGSMELL]);
                int nofc = Integer.parseInt(nextLine[SFL_IX_NOFC]);
                int lofc = Integer.parseInt(nextLine[SFL_IX_LOFC]);
                int nocu = Integer.parseInt(nextLine[SFL_IX_NOCU]);

                Feature feature = new Feature(featName, lofc, nofc, nocu, smellScore);

                // if (lofc >= conf.getLargeFeatureLofcThresh() || nofc >=
                // conf.getLargeFeatureNofcThresh())
                // featMap.put(featName, smellScore);
                featMap.put(featName, feature);
            }
        } catch (IOException e1) {
            throw new RuntimeException("Fehler beim Lesen der Datei " + skunkFeatureLocationsCsv,
                    e1);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // We don't care if closing the reader fails.
                }
            }
        }

        return featMap;
    }

    /**
     * @param date
     * @return
     */
    public Set<String> getFilesInSnapshot(Snapshot snapshot) {
        Set<String> resultSet = new HashSet<>();
        Date snapshotDate = snapshot.getSnapshotDate();

        CSVReader reader = null;
        try {
            reader = new CSVReader(new FileReader(conf.projectAnalysisFile()));
            String[] nextLine;
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            while ((nextLine = reader.readNext()) != null) {
                String fileName = nextLine[0];
                String dateStr = nextLine[1];

                Date verDate = null;
                try {
                    verDate = formatter.parse(dateStr);
                } catch (ParseException e) {
                    throw new RuntimeException(
                            "Error parsing " + conf.projectAnalysisFile().getAbsolutePath(), e);
                }

                if (verDate.equals(snapshotDate))
                    resultSet.add(fileName);
            }
        } catch (IOException e1) {
            throw new RuntimeException(
                    "Error reading file " + conf.projectAnalysisFile().getAbsolutePath(), e1);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // We don't care if closing the reader fails.
                }
            }
        }

        return resultSet;
    }

    /**
     * Names of the files that exhibit the given smell in the given snapshot,
     * along with the smelliness scores assigned to them. Files that are not
     * smelly are not included in the returned map. Put differently, the map
     * values are always non-empty.
     *
     * @param snapshot
     * @param smell
     * @return Map from filename of a smelly file to its smelliness scores. Each
     * smelliness score list will contain at least one entry.
     */
    public Map<String, List<Double>> getSmells(Snapshot snapshot, final Smell smell) {
        Map<String, List<Double>> scoresByFilename = new HashMap<>();
        Date snapshotDate = snapshot.getSnapshotDate();

        CSVReader reader = null;
        File csvFile = conf.smellOverviewFile(smell);
        try {
            reader = new CSVReader(new FileReader(csvFile));
            String[] nextLine;
            DateFormat dateParser = new SimpleDateFormat("EEE MMM dd kk:mm:ss z yyyy",
                    Locale.ENGLISH);
            NumberFormat scoreParser = NumberFormat.getInstance(Locale.ENGLISH);

            while ((nextLine = reader.readNext()) != null) {
                String filename = nextLine[0];
                String dateStr = nextLine[1];
                String scoreStr = nextLine[2];

                Date verDate;
                try {
                    verDate = dateParser.parse(dateStr);
                } catch (ParseException e) {
                    throw new RuntimeException(
                            "Error parsing date " + dateStr + " in " + csvFile.getAbsolutePath(),
                            e);
                }

                if (!verDate.equals(snapshotDate)) {
                    continue;
                }

                double score;
                try {
                    Number scoreNum = scoreParser.parse(scoreStr);
                    score = scoreNum.doubleValue();
                } catch (ParseException e) {
                    throw new RuntimeException(
                            "Error parsing double " + scoreStr + " in " + csvFile.getAbsolutePath(),
                            e);
                }

                List<Double> scores = scoresByFilename.get(filename);
                if (scores == null) {
                    scores = new ArrayList<>();
                    scoresByFilename.put(filename, scores);
                }
                scores.add(score);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading file " + csvFile.getAbsolutePath(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // We don't care if closing the reader fails.
                }
            }
        }

        return scoresByFilename;
    }

    /**
     * @param projectInfo
     * @return
     */
    private List<Date> getProjectDates() {
        List<Date> resultList = new ArrayList<>();
        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        CSVReader reader = null;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(conf.projectInfoFile());
            reader = new CSVReader(fileReader);
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                String dateStr = nextLine[1];
                Date verDate = null;
                try {
                    verDate = formatter.parse(dateStr);
                } catch (ParseException e) {
                    throw new RuntimeException("Error parsing date in  file "
                            + conf.projectInfoFile().getAbsolutePath(), e);
                }

                resultList.add(verDate);
            }
        } catch (IOException e1) {
            throw new RuntimeException(
                    "Error reading file " + conf.projectInfoFile().getAbsolutePath(), e1);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // We don't care if closing the reader fails.
                }
            } else if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    // We don't care if closing the reader fails.
                }
            }
        }

        return resultList;
    }

    /**
     * @return Changed files for the given snapshot
     */
    public SortedMap<FileChangeHunk, String> getChangedFiles(Snapshot s) {
        return changedFilesBySnapshot.get(s);
    }

    /**
     * @return Fixed files for the given snapshot
     */
    public SortedMap<FileChangeHunk, String> getFixedFiles(Snapshot s) {
        return fixedFilesBySnapshot.get(s);
    }

    /**
     * @return Snapshots, ordered by date
     */
    public SortedMap<Date, Snapshot> getSnapshots() {
        return snapshots;
    }
}
