package de.ovgu.skunk.bugs.createsnapshots.main;

import de.ovgu.skunk.bugs.correlate.main.SnapshotDirMissingStrategy;

/**
 * Controls what is to be done with a project, i.e., check it out and create snapshots, preprocess its snapshots, detect
 * smells
 * <p>
 * Created by wfenske on 06.04.17.
 */
public enum SnapshotProcessingMode {

    CHECKOUT(1) {
        @Override
        public ISnapshotProcessingModeStrategy getNewStrategyInstance(CreateSnapshotsConfig conf) {
            return new CheckoutStrategy(conf);
        }

        @Override
        public SnapshotDirMissingStrategy snapshotDirMissingStrategy() {
            return SnapshotDirMissingStrategy.CREATE_IF_MISSING;
        }
    },

    PREPROCESS(2) {
        @Override
        public ISnapshotProcessingModeStrategy getNewStrategyInstance(CreateSnapshotsConfig conf) {
            return new PreprocessStrategy(conf);
        }
    },

    DETECTSMELLS(4) {
        @Override
        public ISnapshotProcessingModeStrategy getNewStrategyInstance(CreateSnapshotsConfig conf) {
            return new DetectSmellsStrategy(conf);
        }
    };

    SnapshotProcessingMode(int defaultNumberOfWorkerThreads) {
        this.defaultNumberOfWorkerThreads = defaultNumberOfWorkerThreads;
    }

    private final int defaultNumberOfWorkerThreads;

    public abstract ISnapshotProcessingModeStrategy getNewStrategyInstance(CreateSnapshotsConfig conf);

    public int defaultNumberOfWorkerThreads() {
        return this.defaultNumberOfWorkerThreads;
    }

    public SnapshotDirMissingStrategy snapshotDirMissingStrategy() {
        return SnapshotDirMissingStrategy.THROW_IF_MISSING;
    }
}
