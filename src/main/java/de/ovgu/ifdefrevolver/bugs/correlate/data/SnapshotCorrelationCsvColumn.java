package de.ovgu.ifdefrevolver.bugs.correlate.data;

import de.ovgu.ifdefrevolver.bugs.correlate.main.Smell;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public enum SnapshotCorrelationCsvColumn {
    FILENAME {
        @Override
        public Object getMergedFileValue(MergedFileInfo f) {
            return f.getFilename();
        }

        @Override
        Object parseCsvItem(String item) {
            return item;
        }
    },
    AB_COUNT {
        @Override
        public Object getMergedFileValue(MergedFileInfo f) {
            return f.getSmellCount(Smell.AB);
        }

        @Override
        Object parseCsvItem(String item) {
            return parseIntOrDie(item, this);
        }
    },
    AF_COUNT {
        @Override
        public Object getMergedFileValue(MergedFileInfo f) {
            return f.getSmellCount(Smell.AF);
        }

        @Override
        Object parseCsvItem(String item) {
            return parseIntOrDie(item, this);
        }
    },
    LF_COUNT {
        @Override
        public Object getMergedFileValue(MergedFileInfo f) {
            return f.getSmellCount(Smell.LF);
        }

        @Override
        Object parseCsvItem(String item) {
            return parseIntOrDie(item, this);
        }
    },
    ANY_SMELL_COUNT {
        @Override
        public Object getMergedFileValue(MergedFileInfo f) {
            return f.getTotalSmellCount();
        }

        @Override
        Object parseCsvItem(String item) {
            return parseIntOrDie(item, this);
        }
    },
    FIX_COUNT {
        @Override
        public Object getMergedFileValue(MergedFileInfo f) {
            return f.getFixCount();
        }

        @Override
        Object parseCsvItem(String item) {
            return parseIntOrDie(item, this);
        }
    },
    CHANGE_COUNT {
        @Override
        public Object getMergedFileValue(MergedFileInfo f) {
            return f.getChangeCount();
        }

        @Override
        Object parseCsvItem(String item) {
            return parseIntOrDie(item, this);
        }
    },
    SLOC {
        @Override
        public Object getMergedFileValue(MergedFileInfo f) {
            return f.getSourceLinesOfCode();
        }

        @Override
        Object parseCsvItem(String item) {
            return parseIntOrDie(item, this);
        }
    };

    public abstract Object getMergedFileValue(MergedFileInfo f);

    /**
     * Convert the merged file object into a line in CSV format, using
     * &quot;,&quot; as the separator. CSV column values appear in the order
     * that this enum's elements are returned by the {@link #values()} method.
     *
     * @param f
     * @return String representation of the given file in CSV format, using
     * &quot;,&quot; as the separator. The line does not include a
     * newline terminator.
     */
    public static String toCsv(MergedFileInfo f) {
        List<Object> colValues = new ArrayList<>(values().length);
        for (SnapshotCorrelationCsvColumn c : values()) {
            Object v = c.getMergedFileValue(f);
            colValues.add(v);
        }
        return StringUtils.join(colValues, ',');
    }

    private static Object parseIntOrDie(String item, SnapshotCorrelationCsvColumn field) {
        try {
            return Integer.parseInt(item);
        } catch (NumberFormatException e) {
            throw new RuntimeException(
                    "Failed to parse " + field.name() + " value from `" + item + "'", e);
        }
    }

    /**
     * @param csvLineItems
     * @return Parsed value of the appropriate type, e.g., String or Integer
     */
    public Object parseFromCsv(String[] csvLineItems) {
        String item = csvLineItems[ordinal()];
        return parseCsvItem(item);
    }

    abstract Object parseCsvItem(String item);
}
