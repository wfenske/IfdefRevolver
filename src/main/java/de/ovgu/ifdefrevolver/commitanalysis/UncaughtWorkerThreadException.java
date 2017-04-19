package de.ovgu.ifdefrevolver.commitanalysis;

/**
 * <p>This exception is meant to be thrown when a worker thread is terminated abnormally, e.g., due to an out of memory
 * exception.</p>
 * <p> Created by wfenske on 19.04.17. </p>
 */
public class UncaughtWorkerThreadException extends Exception {
    public UncaughtWorkerThreadException(Throwable cause) {
        super(cause);
    }
}
