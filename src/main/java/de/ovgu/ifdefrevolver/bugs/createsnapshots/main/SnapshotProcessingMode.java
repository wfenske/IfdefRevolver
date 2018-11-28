package de.ovgu.ifdefrevolver.bugs.createsnapshots.main;

import de.ovgu.ifdefrevolver.bugs.correlate.main.SnapshotDirMissingStrategy;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;

/**
 * Controls what is to be done with a project, i.e., check it out and create snapshots, preprocess its snapshots, detect
 * smells
 * <p>
 * Created by wfenske on 06.04.17.
 */
public enum SnapshotProcessingMode {

    CHECKOUT(1) {
        @Override
        public ISnapshotProcessingModeStrategy getNewStrategyInstance(CommitsDistanceDb commitsDb, CreateSnapshotsConfig conf) {
            return new CheckoutStrategy(commitsDb, conf);
        }

        @Override
        public SnapshotDirMissingStrategy snapshotDirMissingStrategy() {
            return SnapshotDirMissingStrategy.CREATE_IF_MISSING;
        }
    },

    PREPROCESS(2) {
        @Override
        public ISnapshotProcessingModeStrategy getNewStrategyInstance(CommitsDistanceDb commitsDb, CreateSnapshotsConfig conf) {
            return new PreprocessStrategy(commitsDb, conf);
        }
    },

    DETECTSMELLS(4) {
        @Override
        public ISnapshotProcessingModeStrategy getNewStrategyInstance(CommitsDistanceDb commitsDb, CreateSnapshotsConfig conf) {
            return new DetectSmellsStrategy(commitsDb, conf);
        }
    };

    SnapshotProcessingMode(int defaultNumberOfWorkerThreads) {
        this.defaultNumberOfWorkerThreads = defaultNumberOfWorkerThreads;
    }

    private final int defaultNumberOfWorkerThreads;

    public abstract ISnapshotProcessingModeStrategy getNewStrategyInstance(CommitsDistanceDb commitsDb, CreateSnapshotsConfig conf);

    public int defaultNumberOfWorkerThreads() {
        return this.defaultNumberOfWorkerThreads;
    }

    public SnapshotDirMissingStrategy snapshotDirMissingStrategy() {
        return SnapshotDirMissingStrategy.THROW_IF_MISSING;
    }
}
