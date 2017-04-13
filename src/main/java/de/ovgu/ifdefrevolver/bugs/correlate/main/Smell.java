/**
 *
 */
package de.ovgu.ifdefrevolver.bugs.correlate.main;

import de.ovgu.ifdefrevolver.bugs.correlate.data.SnapshotCorrelationCsvColumn;

/**
 * Enumerates the names of smells
 *
 * @author wfenske
 */
public enum Smell {
    /**
     * Annotation Bundle
     */
    AB {
        @Override
        public SnapshotCorrelationCsvColumn getSnapshotCorrelationCsvColumn() {
            return SnapshotCorrelationCsvColumn.AB_COUNT;
        }
    },
    /**
     * Annotation File
     */
    AF {
        @Override
        public SnapshotCorrelationCsvColumn getSnapshotCorrelationCsvColumn() {
            return SnapshotCorrelationCsvColumn.AF_COUNT;
        }
    },
    /**
     * Large Feature
     */
    LF {
        @Override
        public SnapshotCorrelationCsvColumn getSnapshotCorrelationCsvColumn() {
            return SnapshotCorrelationCsvColumn.LF_COUNT;
        }
    };

    public abstract SnapshotCorrelationCsvColumn getSnapshotCorrelationCsvColumn();
}
