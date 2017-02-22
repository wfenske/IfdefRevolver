package de.ovgu.skunk.bugs.correlate.processing;

import de.ovgu.skunk.bugs.correlate.data.FileChangeHunk;
import de.ovgu.skunk.bugs.correlate.input.CSVHelper;
import de.ovgu.skunk.bugs.correlate.main.Config;
import de.ovgu.skunk.bugs.correlate.main.Smell;
import de.ovgu.skunk.bugs.correlate.output.SmellCSV;
import de.ovgu.skunk.bugs.createsnapshots.input.FileFinder;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

public class Preprocessing {

    private static Logger log = Logger.getLogger(Preprocessing.class);

    private final Config conf;
    private final CSVHelper csvReader;

    public Preprocessing(Config conf, CSVHelper csvReader) {
        this.conf = conf;
        this.csvReader = csvReader;
    }

    public void preprocessDataForSmell(Smell smell) {
        SmellCSV smellProcessor = new SmellCSV(conf);

        // Anzahl der .csv Dateien checken
        final File pathFind = new File(conf.projectResultsDir(), smell.name() + "Res");
        log.info("Preprocessing smell data in " + pathFind.getAbsolutePath());
        List<File> smellsPerSnapshotFiles = FileFinder.find(pathFind, "(.*\\.csv$)");

        // für LargeFeature Analyse müssen die FileLocations aus der XML in
        // CSV-Form umgewandelt werden, um die analyse dann einheitlich
        // fortführen zu können.

        // Erste Spalte FileName, zweite Spalte Smell Score.
        if (smell == Smell.LF) {
            for (File csvFile : smellsPerSnapshotFiles) {
                File backupCsvFile = ensureLargeFeatureResultsBackedUp(csvFile);
                File dirname = csvFile.getParentFile();
                String basename = csvFile.getName();
                String xmlBasename = basename.replaceFirst("\\.csv$", ".xml");
                File xmlFile = new File(dirname, xmlBasename).getAbsoluteFile();
                smellProcessor.processLargeFeatureXmlFile(backupCsvFile, xmlFile, csvFile);
            }
        }

        int filesCount = smellsPerSnapshotFiles.size();
        log.debug(String.format("Found %d file%s with information on the %s smell.", filesCount,
                filesCount == 1 ? "" : "s", smell.name()));

        Collections.sort(smellsPerSnapshotFiles);

        // Erstes File nehmen und daraus das erste Datum ableiten
        // File startDateFile = smellsPerSnapshotFiles.get(0);
        // startDate = getDateFromFileName(startDateFile);
        // Set<String> prevSmellyFileSet =
        // csvReader.getSmellyFilesFromSnapshotSmellResFile(startDateFile,
        // smell);

        for (File f : smellsPerSnapshotFiles) {
            log.debug("Preprocessing smell information in " + f.getAbsolutePath());
            Date snapshotDate = getDateFromFileName(f);

            Map<String, List<Double>> smellScoresByFilename = csvReader
                    .getSmellyFilesFromSnapshotSmellResFile(smell, f);

            if (log.isTraceEnabled()) {
                for (Entry<String, List<Double>> e : smellScoresByFilename.entrySet()) {
                    log.trace("\tSmelly file: " + e.getKey() + "," + e.getValue().size());
                }
            }

            smellProcessor.appendCurSmells(smell, smellScoresByFilename, snapshotDate);

            //@formatter:off
            /*
            if (startDate.equals(snapshotDate)) {
                // Wenn Anfangs und Enddatum gleich (nur beim ersten mal der
                // Fall), dann schreibe alle Smells in die CSV.
                smellProcessor.appendCurSmells(smellyFileSet, snapshotDate, smell);
            } else {
                // Ansonsten müssen erst die geänderten Daten rausgerechnet
                // werden.

                // lädt alle geänderten Dateien zwischen zwei Daten in ein Set
                Map<String, Integer> curChangedSet = getCurFiles(changedMap, startDate, snapshotDate);

                Set<String> notChangedSet = new HashSet<>(prevSmellyFileSet);
                notChangedSet.removeAll(curChangedSet.keySet());
                notChangedSet.addAll(smellyFileSet);

                // zum schluss dieses Set in die CSV schreiben
                smellProcessor.appendCurSmells(notChangedSet, snapshotDate, smell);
            }

            // prevSmellyFileSet und startDate ändern
            startDate = snapshotDate;
            prevSmellyFileSet = smellyFileSet;
            */
            //@formatter:on
        }

        final File smellOverviewFile = conf.smellOverviewFile(smell);
        if (!smellOverviewFile.exists()) {
            log.info("No smells of type " + smell + " were found. Creating empty results file "
                    + smellOverviewFile.getAbsolutePath());
            try {
                smellOverviewFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(
                        "Error creating empty results file " + smellOverviewFile.getAbsolutePath(),
                        e);
            }
        }
    }

    private static File ensureLargeFeatureResultsBackedUp(File csvFile) {
        final String expectedSuffix = ".csv";
        File dirname = csvFile.getParentFile();
        String basename = csvFile.getName();
        if (!basename.endsWith(expectedSuffix)) {
            throw new IllegalArgumentException("File name does not end in " + expectedSuffix + ": "
                    + csvFile.getAbsolutePath());
        }
        String backupCsvBasename = basename + ".orig";
        File backupCsvFile = new File(dirname, backupCsvBasename);
        if (!backupCsvFile.exists()) {
            log.info("Backing up Large Feature results " + csvFile + " to " + backupCsvFile);
            try {
                Files.copy(csvFile.toPath(), backupCsvFile.toPath());
            } catch (IOException e) {
                throw new RuntimeException("Error backing up Large Feature results " + csvFile
                        + " to " + backupCsvFile, e);
            }
        } else {
            log.debug("Backup of Large Feature results already exists: " + backupCsvFile);
        }
        return backupCsvFile;
    }

    /**
     * Gets the Date from Filename with Format "yyyy-MM-dd"
     *
     * @param f File
     * @return Date
     */
    public static Date getDateFromFileName(File f) {
        Date retDate = null;

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        String filePath = f.getAbsolutePath();
        int slashIdx = filePath.lastIndexOf("/") + 1;
        int pointIdx = filePath.lastIndexOf(".");
        String curDateStr = filePath.substring(slashIdx, pointIdx);
        try {
            retDate = formatter.parse(curDateStr);
        } catch (ParseException e) {
            throw new RuntimeException("Datum konnte nicht korrekt eingelesen werden!", e);
        }

        return retDate;
    }

    /**
     * Lädt alle Dateien aus einer TreeMap<ChangedFile, String> zwischen zwei
     * Daten in ein Set
     *
     * @param bugMap    TreeMap mit allen Dateien im ChangedFile-Objektformat
     * @param startDate
     * @param endDate
     * @return alle Bugfixes zwischen StartDate und EndDate
     */
    private static Map<String, Integer> getCurFiles(SortedMap<FileChangeHunk, String> bugMap,
                                                    Date startDate, Date endDate) {
        Map<String, Integer> result = new HashMap<>();
        for (FileChangeHunk changedFile : bugMap.keySet()) {
            final Date fileDate = changedFile.getCommitDate();
            if (startDate.after(fileDate))
                continue;
            if (endDate.before(fileDate))
                break;
            // curBugSet.add(bugMap.get(keySec));

            if (result.containsKey(changedFile.getFilename())) {
                int counter = result.get(changedFile.getFilename());
                result.put(changedFile.getFilename(), ++counter);
            } else {
                result.put(changedFile.getFilename(), 1);
            }
        }

        return result;
    }

    /**
     * Lädt alle Dateien aus einer TreeMap<ChangedFile, String> zwischen zwei
     * Daten in ein Set
     *
     * @param bugMap    TreeMap mit allen Dateien im ChangedFile-Objektformat
     * @param startDate
     * @param endDate
     * @return alle Bugfixes zwischen StartDate und EndDate
     */
    private static Map<FileChangeHunk, String> getCurMap(Map<FileChangeHunk, String> bugMap,
                                                         Date startDate, Date endDate) {
        Map<FileChangeHunk, String> curBugMap = new HashMap<>();
        for (FileChangeHunk changedFile : bugMap.keySet()) {
            if (startDate.after(changedFile.getCommitDate()))
                continue;
            if (endDate.before(changedFile.getCommitDate()))
                break;

            curBugMap.put(changedFile, changedFile.getFilename());
        }
        return curBugMap;
    }
}
