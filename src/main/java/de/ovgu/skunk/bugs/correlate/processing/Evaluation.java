package de.ovgu.skunk.bugs.correlate.processing;

import com.opencsv.CSVReader;
import de.ovgu.skunk.bugs.correlate.data.SnapshotCorrelationCsvColumn;
import de.ovgu.skunk.bugs.correlate.main.Smell;
import org.apache.commons.math3.stat.StatUtils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Evaluation {
    private static final List<String> COR_OVERVIEW_GROUP_PREFIXES;

    static {
        COR_OVERVIEW_GROUP_PREFIXES = new ArrayList<>();
        COR_OVERVIEW_GROUP_PREFIXES.add("any");
        for (Smell smell : Smell.values()) {
            COR_OVERVIEW_GROUP_PREFIXES.add(smell.name());
        }
        COR_OVERVIEW_GROUP_PREFIXES.add("ABorAF");
    }

    public static enum CorOverviewMetric {
        /**
         * at least one smell
         */
        ANY {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                return smellsInFile.hasAnySmell();
            }
        },
        /**
         * at least one annotation bundle
         */
        AB {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                return smellsInFile.hasSmell(Smell.AB);
            }
        },
        /**
         * at least one annotation file
         */
        AF {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                return smellsInFile.hasSmell(Smell.AF);
            }
        },
        /**
         * at least one large feature
         */
        LF {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                return smellsInFile.hasSmell(Smell.LF);
            }
        },
        /**
         * at least one annotation bundle or one annotation file
         */
        ABorAF {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                return smellsInFile.hasSmell(Smell.AB) || smellsInFile.hasSmell(Smell.AF);
            }
        },
        /**
         * at least one annotation bundle and one annotation file
         */
        ABandAF {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                return smellsInFile.hasSmell(Smell.AB) && smellsInFile.hasSmell(Smell.AF);
            }
        },
        /**
         * at least one annotation bundle or one large feature
         */
        ABorLF {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                return smellsInFile.hasSmell(Smell.AB) || smellsInFile.hasSmell(Smell.LF);
            }
        },
        /**
         * at least one annotation bundle and one large feature
         */
        ABandLF {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                return smellsInFile.hasSmell(Smell.AB) && smellsInFile.hasSmell(Smell.LF);
            }
        },
        /**
         * at least one annotation file or one large feature
         */
        AForLF {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                return smellsInFile.hasSmell(Smell.AF) || smellsInFile.hasSmell(Smell.LF);
            }
        },
        /**
         * at least one annotation file and one large feature
         */
        AFandLF {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                return smellsInFile.hasSmell(Smell.AF) && smellsInFile.hasSmell(Smell.LF);
            }
        },
        /**
         * at least two smells (maybe twice the same smell)
         */
        ANY2 {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                return smellsInFile.getAnySmellCount() >= 2;
            }
        },
        /**
         * at least two different smells (e.g., one annotation bundle and one
         * large feature)
         */
        ANY2Distinct {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                int numDistinctSmells = 0;
                for (Smell smell : Smell.values()) {
                    if (smellsInFile.hasSmell(smell)) {
                        numDistinctSmells++;
                    }
                }
                return numDistinctSmells >= 2;
            }
        },
        /**
         * at least two annotation bundles
         */
        AB2 {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                return smellsInFile.getSmellCount(Smell.AB) >= 2;
            }
        },
        /** at least two annotation bundles */
        /* NOTE: AF2 is impossible due to the nature of the smell */
        /**
         * at least two large features
         */
        LF2 {
            @Override
            public boolean matches(SmellsInFile smellsInFile) {
                return smellsInFile.getSmellCount(Smell.LF) >= 2;
            }
        },;

        /**
         * Tests whether the combination of smells in the given file satisfies
         * the criterion of this metric.
         *
         * @param smellsInFile Information about the smells present within a file
         * @return <code>true</code> if the criterion is fulfilled,
         * <code>false</code> otherwise
         */
        public abstract boolean matches(SmellsInFile smellsInFile);
    }

    public static enum CorOverviewGroupColumn {
        /**
         * # smelly files
         */
        FS {
            @Override
            public String getColumnValue(CorOverviewTab tab) {
                return String.valueOf(tab.fisherTab.getSmellyFileCount());
            }
        },
        /**
         * # non-smelly files
         */
        FNS {
            @Override
            public String getColumnValue(CorOverviewTab tab) {
                return String.valueOf(tab.fisherTab.getNonSmellyFileCount());
            }
        },
        /**
         * Bug-fixes to smelly files
         */
        BS {
            @Override
            public String getColumnValue(CorOverviewTab tab) {
                return String.valueOf(tab.fixesAndSlocTab.smellyFixCount);
            }
        },
        /**
         * Bug-fixes to non-smelly files
         */
        BNS {
            @Override
            public String getColumnValue(CorOverviewTab tab) {
                return String.valueOf(tab.fixesAndSlocTab.nonSmellyFixCount);
            }
        },
        /**
         * # files that are smelly and have been bug-fixed
         */
        FSB {
            @Override
            public String getColumnValue(CorOverviewTab tab) {
                return String.valueOf(tab.fisherTab.smellyFixed);
            }
        },
        /**
         * # files that are smelly and have not been bug-fixed
         */
        FSNB {
            @Override
            public String getColumnValue(CorOverviewTab tab) {
                return String.valueOf(tab.fisherTab.smellyNotFixed);
            }
        },
        /**
         * # files that are not smelly and have been bug-fixed
         */
        FNSB {
            @Override
            public String getColumnValue(CorOverviewTab tab) {
                return String.valueOf(tab.fisherTab.nonSmellyFixed);
            }
        },
        /**
         * # files that are not smelly and have not been bug-fixed
         */
        FNSNB {
            @Override
            public String getColumnValue(CorOverviewTab tab) {
                return String.valueOf(tab.fisherTab.nonSmellyNotFixed);
            }
        },
        /**
         * Mean SLOC of smelly files
         */
        SLOCMeanS {
            @Override
            public String getColumnValue(CorOverviewTab tab) {
                Optional<Integer> o = tab.fixesAndSlocTab.meanSmellySloc();
                return optValueToString(o);
            }
        },
        /**
         * Mean SLOC of non-smelly files
         */
        SLOCMeanNS {
            @Override
            public String getColumnValue(CorOverviewTab tab) {
                Optional<Integer> o = tab.fixesAndSlocTab.meanNonSmellySloc();
                return optValueToString(o);
            }
        },
        /**
         * Median SLOC of smelly files
         */
        SLOCMedianS {
            @Override
            public String getColumnValue(CorOverviewTab tab) {
                Optional<Integer> o = tab.fixesAndSlocTab.medianSmellySloc();
                return optValueToString(o);
            }
        },
        /**
         * Median SLOC of non-smelly files
         */
        SLOCMedianNS {
            @Override
            public String getColumnValue(CorOverviewTab tab) {
                Optional<Integer> o = tab.fixesAndSlocTab.medianNonSmellySloc();
                return optValueToString(o);
            }
        };

        private static String optValueToString(Optional<Integer> o) {
            if (o.isPresent()) {
                return o.get().toString();
            } else {
                return "";
            }
        }

        public abstract String getColumnValue(CorOverviewTab tab);
    }

    /**
     * Table containing four values:
     * <ol>
     * <li>smellyFixed
     * <li>smellyNotFixed
     * <li>nonSmellyFixed
     * <li>nonSmellyNotFixed
     * </ul>
     */
    public static class FisherTab {
        /**
         * Number of files that exhibit the smell <em>and</em> have been fixed.
         */
        public int smellyFixed = 0;
        /**
         * Number of files that exhibit the smell <em>and</em> have <em>not</em>
         * been fixed.
         */
        public int smellyNotFixed = 0;
        /**
         * Number of files that <em>do not</em> exhibit the smell <em>and</em>
         * have been fixed.
         */
        public int nonSmellyFixed = 0;
        /**
         * Number of files that <em>do not</em> exhibit the smell <em>and</em>
         * have <em>not</em> been fixed.
         */
        public int nonSmellyNotFixed = 0;

        /**
         * @return Total number of files that exhibited the smell
         */
        public int getSmellyFileCount() {
            return smellyFixed + smellyNotFixed;
        }

        /**
         * @return Total number of files that <em>did not</em> exhibit the smell
         */
        public int getNonSmellyFileCount() {
            return nonSmellyFixed + nonSmellyNotFixed;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + nonSmellyFixed;
            result = prime * result + nonSmellyNotFixed;
            result = prime * result + smellyFixed;
            result = prime * result + smellyNotFixed;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (!(obj instanceof FisherTab))
                return false;
            FisherTab other = (FisherTab) obj;
            if (nonSmellyFixed != other.nonSmellyFixed)
                return false;
            if (nonSmellyNotFixed != other.nonSmellyNotFixed)
                return false;
            if (smellyFixed != other.smellyFixed)
                return false;
            if (smellyNotFixed != other.smellyNotFixed)
                return false;
            return true;
        }
    }

    /**
     * Table containing four values:
     * <ol>
     * <li>smellyFixCount -- number of times that smelly files have been fixed
     * <li>nonSmellyFixCount -- number of times that non-smelly files have been
     * fixed
     * <li>SLOCs of smelly files
     * <li>SLOCs of non-smelly files
     * </ul>
     */
    public static class FixesAndSlocTab {
        /**
         * Number of times that smelly files have been fixed (there may be
         * multiple fixes to one file)
         */
        public int smellyFixCount = 0;

        /**
         * Number of times that non-smelly files have been fixed (there may be
         * multiple fixes to one file)
         */
        public int nonSmellyFixCount = 0;

        /**
         * SLOC of smelly files (irregardless of smell)
         */
        public List<Integer> smellySloc = new ArrayList<>();

        /**
         * SLOC of non-smelly files (irregardless of smell)
         */
        public List<Integer> nonSmellySloc = new ArrayList<>();

        public Optional<Integer> meanSmellySloc() {
            return meanSloc(smellySloc);
        }

        public Optional<Integer> meanNonSmellySloc() {
            return meanSloc(nonSmellySloc);
        }

        public Optional<Integer> medianSmellySloc() {
            return medianSloc(smellySloc);
        }

        public Optional<Integer> medianNonSmellySloc() {
            return medianSloc(nonSmellySloc);
        }

        private Optional<Integer> meanSloc(Collection<Integer> locs) {
            double[] vals = toDoubleArray(locs);
            double mean = StatUtils.mean(vals);
            return toOptionalInt(mean);
        }

        private static Optional<Integer> medianSloc(Collection<Integer> locs) {
            double[] vals = toDoubleArray(locs);
            double median = StatUtils.percentile(vals, 50);
            return toOptionalInt(median);
        }

        private static Optional<Integer> toOptionalInt(double v) {
            if (Double.isNaN(v))
                return Optional.empty();
            else {
                return Optional.of((int) Math.round(v));
            }
        }

        private static double[] toDoubleArray(Collection<Integer> ints) {
            double[] vals = new double[ints.size()];

            int ixInsert = 0;
            for (Integer v : ints) {
                vals[ixInsert++] = v;
            }
            return vals;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + nonSmellyFixCount;
            result = prime * result + ((nonSmellySloc == null) ? 0 : nonSmellySloc.hashCode());
            result = prime * result + smellyFixCount;
            result = prime * result + ((smellySloc == null) ? 0 : smellySloc.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (!(obj instanceof FixesAndSlocTab))
                return false;
            FixesAndSlocTab other = (FixesAndSlocTab) obj;
            if (nonSmellyFixCount != other.nonSmellyFixCount)
                return false;
            if (nonSmellySloc == null) {
                if (other.nonSmellySloc != null)
                    return false;
            } else if (!nonSmellySloc.equals(other.nonSmellySloc))
                return false;
            if (smellyFixCount != other.smellyFixCount)
                return false;
            if (smellySloc == null) {
                if (other.smellySloc != null)
                    return false;
            } else if (!smellySloc.equals(other.smellySloc))
                return false;
            return true;
        }
    }

    public static class CorOverviewTab {
        public FisherTab fisherTab = new FisherTab();
        public FixesAndSlocTab fixesAndSlocTab = new FixesAndSlocTab();

        public void updateTabs(boolean hasSmell, int fixCount, int sloc) {
            CorOverviewTab tab = this;
            final boolean wasFixed = fixCount > 0;
            if (hasSmell) {
                if (wasFixed)
                    tab.fisherTab.smellyFixed++;
                else
                    tab.fisherTab.smellyNotFixed++;
                tab.fixesAndSlocTab.smellyFixCount += fixCount;
                tab.fixesAndSlocTab.smellySloc.add(sloc);
            } else {
                if (wasFixed)
                    tab.fisherTab.nonSmellyFixed++;
                else
                    tab.fisherTab.nonSmellyNotFixed++;
                tab.fixesAndSlocTab.nonSmellyFixCount += fixCount;
                tab.fixesAndSlocTab.nonSmellySloc.add(sloc);
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((fisherTab == null) ? 0 : fisherTab.hashCode());
            result = prime * result + ((fixesAndSlocTab == null) ? 0 : fixesAndSlocTab.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (!(obj instanceof CorOverviewTab))
                return false;
            CorOverviewTab other = (CorOverviewTab) obj;
            if (fisherTab == null) {
                if (other.fisherTab != null)
                    return false;
            } else if (!fisherTab.equals(other.fisherTab))
                return false;
            if (fixesAndSlocTab == null) {
                if (other.fixesAndSlocTab != null)
                    return false;
            } else if (!fixesAndSlocTab.equals(other.fixesAndSlocTab))
                return false;
            return true;
        }
    }

    public static class SmellsInFile {
        private Map<Smell, Integer> smellsInLine = new HashMap<>();

        public static SmellsInFile fromCsv(String[] nextLine) {
            SmellsInFile r = new SmellsInFile();
            for (Smell smell : Smell.values()) {
                SnapshotCorrelationCsvColumn col = smell.getSnapshotCorrelationCsvColumn();
                int count = (Integer) col.parseFromCsv(nextLine);
                r.smellsInLine.put(smell, count);
            }
            return r;
        }

        public int getSmellCount(Smell smell) {
            return smellsInLine.get(smell);
        }

        public boolean hasSmell(Smell smell) {
            return getSmellCount(smell) > 0;
        }

        public int getAnySmellCount() {
            int r = 0;
            for (Smell smell : Smell.values()) {
                r += getSmellCount(smell);
            }
            return r;
        }

        public boolean hasAnySmell() {
            for (Smell smell : Smell.values()) {
                if (hasSmell(smell))
                    return true;
            }
            return false;
        }
    }

    public static String getCorOverviewCsvHeader() {
        StringBuilder r = new StringBuilder();

        r.append("Snapshot"); // Holds snapshot dates
		/*
		 * for (String prefix : COR_OVERVIEW_GROUP_PREFIXES) { for
		 * (CorOverviewGroupColumn c : CorOverviewGroupColumn.values()) {
		 * r.append(','); r.append(prefix).append('_').append(c.name()); } }
		 */

        for (CorOverviewMetric metric : CorOverviewMetric.values()) {
            String prefix = metric.name();
            for (CorOverviewGroupColumn c : CorOverviewGroupColumn.values()) {
                r.append(',');
                r.append(prefix).append('_').append(c.name());
            }
        }

        return r.toString();
    }

    public static void evalFile(File snapshotCorrelatedData, int minFileSloc,
                                File corOverviewCsvOut) {

        // @formatter:off
		/*
		double smellFixed = 0;
		double smellChanged = 0;
		double smellNotFixed = 0;
		double smellNotChanged = 0;
		double nonSmellFixed = 0;
		double nonSmellChanged = 0;
		double nonSmellNotFixed = 0;
		double nonSmellNotChanged = 0;
		*/
        // @formatter:on

        /** {smellyFix, smellyNotFix, nonSmellyFix, nonSmellyNotFix} */
        Map<Smell, CorOverviewTab> singleSmellTabs = new HashMap<>();

        for (Smell smell : Smell.values()) {
            singleSmellTabs.put(smell, new CorOverviewTab());
        }

        CorOverviewTab abOrAfTab = new CorOverviewTab();

        CorOverviewTab anySmellTab = new CorOverviewTab();

        Map<CorOverviewMetric, CorOverviewTab> smellTabs = new HashMap<>();
        for (CorOverviewMetric m : CorOverviewMetric.values()) {
            smellTabs.put(m, new CorOverviewTab());
        }

        // @formatter:off
		/*
        int smFixCount = 0;
        int smChangeCount = 0;
        int nsFixCount = 0;
        int nsChangeCount = 0;
        int smellySizeAmount = 0;
        int nonSmellySizeAmount = 0;
        */
        // @formatter:on
        final Date snapshotDate = Preprocessing.getDateFromFileName(snapshotCorrelatedData);

        FileReader fileReader = null;
        CSVReader reader = null;
        try {
            fileReader = new FileReader(snapshotCorrelatedData);
            reader = new CSVReader(fileReader);
            String[] nextLine;
            reader.readNext(); // Skip header
            while ((nextLine = reader.readNext()) != null) {
                final int sloc = (Integer) SnapshotCorrelationCsvColumn.SLOC.parseFromCsv(nextLine);

                if (sloc < minFileSloc) {
                    continue;
                }

                final int totalSmellCount = (Integer) SnapshotCorrelationCsvColumn.ANY_SMELL_COUNT
                        .parseFromCsv(nextLine);

                final int fixCount = (Integer) SnapshotCorrelationCsvColumn.FIX_COUNT
                        .parseFromCsv(nextLine);

                // final int changeCount = (Integer)
                // SnapshotCorrelationCsvColumn.CHANGE_COUNT.parseFromCsv(nextLine);

                final SmellsInFile smellsInLine = SmellsInFile.fromCsv(nextLine);

                anySmellTab.updateTabs(totalSmellCount > 0, fixCount, sloc);

                // Abfrage für die einzelnen Smells
                for (Smell smell : Smell.values()) {
                    boolean fileHasTheSmell = smellsInLine.hasSmell(smell);
                    CorOverviewTab singleSmellTab = singleSmellTabs.get(smell);
                    singleSmellTab.updateTabs(fileHasTheSmell, fixCount, sloc);
                }

                boolean hasAbOrAf = smellsInLine.hasSmell(Smell.AB)
                        || smellsInLine.hasSmell(Smell.AF);
                abOrAfTab.updateTabs(hasAbOrAf, fixCount, sloc);

                for (CorOverviewMetric m : CorOverviewMetric.values()) {
                    CorOverviewTab tab = smellTabs.get(m);
                    boolean matches = m.matches(smellsInLine);
                    tab.updateTabs(matches, fixCount, sloc);
                }

                assertTabsEqual(anySmellTab, smellTabs, CorOverviewMetric.ANY);
                assertTabsEqual(singleSmellTabs.get(Smell.AB), smellTabs, CorOverviewMetric.AB);
                assertTabsEqual(singleSmellTabs.get(Smell.AF), smellTabs, CorOverviewMetric.AF);
                assertTabsEqual(singleSmellTabs.get(Smell.LF), smellTabs, CorOverviewMetric.LF);
                assertTabsEqual(abOrAfTab, smellTabs, CorOverviewMetric.ABorAF);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading file " + snapshotCorrelatedData, e);
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
                    // Don't care
                }
            }
        }

		/*
		 * Collection<CorOverviewTab> allTabs = new ArrayList<>();
		 * allTabs.add(anySmellTab); for (Smell smell : Smell.values()) {
		 * CorOverviewTab singleSmellTab = singleSmellTabs.get(smell);
		 * allTabs.add(singleSmellTab); } allTabs.add(abOrAfTab);
		 */

        final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

        FileWriter fileWriter = null;
        BufferedWriter buff = null;
        try {
            fileWriter = new FileWriter(corOverviewCsvOut, true);
            buff = new BufferedWriter(fileWriter);
            buff.write(dateFormatter.format(snapshotDate));

			/*
			 * for (CorOverviewTab tab : allTabs) { for (CorOverviewGroupColumn
			 * c : CorOverviewGroupColumn.values()) { buff.write(','); String v
			 * = c.getColumnValue(tab); buff.write(v); } }
			 */
            for (CorOverviewMetric metric : CorOverviewMetric.values()) {
                CorOverviewTab tab = smellTabs.get(metric);
                for (CorOverviewGroupColumn c : CorOverviewGroupColumn.values()) {
                    buff.write(',');
                    String v = c.getColumnValue(tab);
                    buff.write(v);
                }
            }

            buff.newLine();
        } catch (IOException e) {
            throw new RuntimeException("Error writing file " + corOverviewCsvOut.getAbsolutePath(),
                    e);
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
    }

    private static void assertTabsEqual(CorOverviewTab manualSmellTab,
                                        Map<CorOverviewMetric, CorOverviewTab> autoSmellTabs, CorOverviewMetric metric) {
        CorOverviewTab autoSmellTab = autoSmellTabs.get(metric);
        if (!manualSmellTab.equals(autoSmellTab)) {
            throw new AssertionError("Correlation tabs do not match for metric " + metric);
        }
    }
}
