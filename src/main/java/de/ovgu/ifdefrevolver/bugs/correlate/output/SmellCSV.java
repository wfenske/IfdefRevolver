package de.ovgu.ifdefrevolver.bugs.correlate.output;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Feature;
import de.ovgu.ifdefrevolver.bugs.correlate.data.LargeFeatureCsvColumns;
import de.ovgu.ifdefrevolver.bugs.correlate.input.CSVHelper;
import de.ovgu.ifdefrevolver.bugs.correlate.main.Config;
import de.ovgu.ifdefrevolver.bugs.correlate.main.Smell;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

public class SmellCSV {
    private static Logger log = Logger.getLogger(SmellCSV.class);
    private final Config conf;

    public SmellCSV(Config conf) {
        this.conf = conf;
    }

    /**
     * @param smell                 The smell to analyze
     * @param smellScoresByFilename
     * @param snapshotDate
     */
    public void appendCurSmells(Smell smell, Map<String, List<Double>> smellScoresByFilename,
                                Date snapshotDate) {
        // In CSV Datei schreiben
        File csvOut = conf.smellOverviewFile(smell);

        FileWriter fileWriter = null;
        BufferedWriter buff = null;
        try {
            fileWriter = new FileWriter(csvOut, true);
            buff = new BufferedWriter(fileWriter);
            for (Entry<String, List<Double>> e : smellScoresByFilename.entrySet()) {
                final String fn = e.getKey();
                List<Double> scores = e.getValue();
                for (Double score : scores) {
                    String scoreStr = String.format(Locale.ENGLISH, "%f", score);
                    buff.write(fn + "," + snapshotDate + "," + scoreStr);
                    buff.newLine();
                }
            }
        } catch (IOException e1) {
            throw new RuntimeException("Error appending to " + csvOut.getAbsolutePath(), e1);
        } finally {
            flushAndCloseBuffer(csvOut, buff, fileWriter);
        }

    }

    /**
     * Perform Large Feature detection and write the results to a CSV file
     * (parameter largeFeatureCsvOut)
     *
     * @param origFeatureLocationCsv Large feature detection results CSV file, as produced by Skunk
     * @param featureLocationXml     Locations of all features as an XML file, produced by Skunk
     * @param csvOut                 Rewritten Large Feature detection results file (CSV format)
     */
    public void processLargeFeatureXmlFile(File origFeatureLocationCsv, File featureLocationXml,
                                           File csvOut) {
        // für LargeFeature Analyse müssen die FileLocations aus der XML in csv
        // Form umgewandelt werden, um die analyse dann einheitlich fortführen
        // zu können.

        // Erste Spalte FileName - zweite Spalte SmellScore

        Map<String, Feature> featureMap = (new CSVHelper(conf))
                .getFeaturesByName(origFeatureLocationCsv);
        Map<String, Feature> largeFeatures = filterLargeFeatures(featureMap);

        ensureNewLargeFeatureResultsFile(csvOut);

        int lfInstances = 0;
        BufferedWriter buff = null;
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(csvOut);
            buff = new BufferedWriter(fileWriter);

            // Write header
            for (int ix = 0; ix < LargeFeatureCsvColumns.values().length; ix++) {
                LargeFeatureCsvColumns col = LargeFeatureCsvColumns.values()[ix];
                if (ix > 0) {
                    buff.write(",");
                }
                buff.write(col.name());
            }
            buff.newLine();

            // Write body
            final Document featureLocationDoc = parseFeatureLocationXml(featureLocationXml);
            // die eingelesenen Features mit der XML abgleichen und
            // Filenamen pro Feature auslesen
            for (Entry<String, Feature> featureEntry : largeFeatures.entrySet()) {
                String featureName = featureEntry.getKey();
                Feature feature = featureEntry.getValue();

                Set<String> fileSetXML = findFilesParticipatingInFeature(featureLocationDoc,
                        featureName);

                // FeatureNamen und Score in CSV schreiben
                for (String fileName : fileSetXML) {
                    // @formatter:off
                    buff.write(/* 0 */ fileName + "," + /* 1 */ feature.getLgScore() + "," + /* 2 */ feature.getName()
                            + "," + /* 3 */ feature.getLinesOfFeatureCode() + ","
                            + /* 4 */ feature.getNumberOfOccurrences() + ","
                            + /* 5 */ feature.getNumberOfCompilationUnits());
                    // @formatter:on
                    buff.newLine();
                    lfInstances++;
                }
            }
        } catch (IOException e1) {
            throw new RuntimeException("Error writing file " + csvOut.getAbsolutePath(), e1);
        } finally {
            flushAndCloseBuffer(csvOut, buff, fileWriter);
        }

        log.debug("Reported " + lfInstances + " file(s) as participating in a Large Feature in "
                + csvOut.getAbsolutePath());
    }

    private void ensureNewLargeFeatureResultsFile(File resultFile) {
        if (resultFile.exists()) {
            log.info("Overwriting Large Feature results in " + resultFile.getAbsolutePath());
            if (!resultFile.delete()) {
                throw new RuntimeException("Error deleting old Large Feature results in "
                        + resultFile.getAbsolutePath());
            }
            try {
                if (!resultFile.createNewFile()) {
                    throw new RuntimeException("Error recreating Large Feature results in "
                            + resultFile.getAbsolutePath());
                }
            } catch (IOException e) {
                throw new RuntimeException(
                        "Error recreating Large Feature results in " + resultFile.getAbsolutePath(),
                        e);
            }
        }
    }

    private void flushAndCloseBuffer(File csvOut, BufferedWriter buff, FileWriter fileWriter) {
        if (buff != null) {
            try {
                buff.flush();
                buff.close();
            } catch (IOException e) {
                log.warn(
                        "Error flushing or closing buffered writer for " + csvOut.getAbsolutePath(),
                        e);
            }
        } else {
            // The buffer also closes the file writer --> we only need to
            // close the fileWriter ourselves if the buffer was not created.
            if (fileWriter != null) {
                try {
                    fileWriter.flush();
                    fileWriter.close();
                } catch (IOException e) {
                    log.warn(
                            "Error flushing or closing file writer for " + csvOut.getAbsolutePath(),
                            e);
                }
            }
        }
    }

    private Map<String, Feature> filterLargeFeatures(Map<String, Feature> featureMap) {
        final int numAllFeatures = featureMap.size();
        Map<String, Feature> allLargeFeatures = new HashMap<>();

        Map<String, Feature> largestFeaturesByLoc = filterTopPercent(featureMap,
                conf.getLargeFeatureLocPercentage(), Feature.LOC_COMP);
        log.debug("Number of Large features by LOC: " + largestFeaturesByLoc.size() + " (out of "
                + numAllFeatures + ")");
        allLargeFeatures.putAll(largestFeaturesByLoc);

        Map<String, Feature> mostScatteredFeaturesByNumCompilationUnits = filterTopPercent(
                featureMap, conf.getLargeFeatureNumCompilationUnitsPercentage(),
                Feature.NUM_COMPILATION_UNITS);
        log.debug("Number of Large features by NOCU: "
                + mostScatteredFeaturesByNumCompilationUnits.size() + " (out of " + numAllFeatures
                + ")");
        allLargeFeatures.putAll(mostScatteredFeaturesByNumCompilationUnits);

        Map<String, Feature> mostScatteredFeaturesByOccurrence = filterTopPercent(featureMap,
                conf.getLargeFeatureOccurrencePercentage(), Feature.NUM_OCCURRENCES_COMP);
        log.debug("Number of Large features by NOFL: " + mostScatteredFeaturesByOccurrence.size()
                + " (out of " + numAllFeatures + ")");
        allLargeFeatures.putAll(mostScatteredFeaturesByOccurrence);

        if (log.isDebugEnabled()) {
            for (Entry<String, Feature> featureEntry : featureMap.entrySet()) {
                String featureName = featureEntry.getKey();
                Feature feature = featureEntry.getValue();

                if (!allLargeFeatures.containsKey(featureName)) {
                    // log.debug("Skipping non-large feature " + feature);
                } else {
                    log.debug("Found large feature: " + feature);
                }
            }
        }

        return allLargeFeatures;
    }

    /**
     * @param featureLocationDoc the already-parsed Skunk document containing feature location
     *                           information
     * @param featureName        the name of the feature in question
     * @return
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    private static Set<String> findFilesParticipatingInFeature(Document featureLocationDoc,
                                                               String featureName) {
        Set<String> fileNames = new HashSet<>();
        NodeList featList = featureLocationDoc.getElementsByTagName(
                de.ovgu.skunk.detection.data.Feature.class.getCanonicalName());
        log.trace("Found " + featList.getLength() + " feature node(s).");
        for (int j = 0; j < featList.getLength(); j++) {
            Element el = (Element) featList.item(j);

            NodeList nameL = el.getElementsByTagName("Name");
            String name = nameL.item(0).getTextContent();

            if (name.equals(featureName)) {
                NodeList fileList = el.getElementsByTagName("compilationFiles");
                Element testEle = (Element) fileList.item(0);

                NodeList testList = testEle.getElementsByTagName("string");
                for (int i = 0; i < testList.getLength(); i++) {
                    Node test = testList.item(i);
                    // log.debug("getFilesFromXML: test content: " +
                    // test.getTextContent());
                    fileNames.add(test.getTextContent());
                }
            }
        }

        return fileNames;
    }

    private static Document parseFeatureLocationXml(File featureLocationXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document featureLocationDoc = builder.parse(featureLocationXml);
            featureLocationDoc.getDocumentElement().normalize();
            return featureLocationDoc;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(
                    "Error parsing feature location XML " + featureLocationXml.getAbsolutePath(),
                    e);

        }
    }

    private static Map<String, Feature> filterTopPercent(Map<String, Feature> featureMap,
                                                         double percentage, Comparator<? super Feature> cmp) {
        List<Feature> topFeatures = takeTopPercentile(featureMap.values(), cmp, percentage);
        return mappifyFeatures(topFeatures);
    }

    private static <E, C extends Comparator<? super E>> List<E> takeTopPercentile(
            Collection<E> elems, C cmp, double percentage) {
        if (percentage < 0.0 || percentage > 100.0) {
            throw new IllegalArgumentException(
                    "Percentage values must lie between 0.0 and 100.0. Got " + percentage);
        }

        if (percentage == 0.0) {
            return new ArrayList<>(0);
        }

        List<E> sorted = new ArrayList<>(elems);
        int len = elems.size();
        if (len == 0) {
            return sorted;
        }
        Collections.sort(sorted, cmp);
        int ixStartOfTopElems = (int) Math.round(len - len * percentage / 100.0);
        ixStartOfTopElems = Math.max(0, Math.min(ixStartOfTopElems, len - 1));
        return new ArrayList<>(sorted.subList(ixStartOfTopElems, len));
    }

    private static Map<String, Feature> mappifyFeatures(List<Feature> topFeatures) {
        Map<String, Feature> result = new HashMap<>();
        for (Feature f : topFeatures) {
            result.put(f.getName(), f);
        }
        return result;
    }
}
