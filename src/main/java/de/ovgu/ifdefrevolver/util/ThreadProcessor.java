package de.ovgu.ifdefrevolver.util;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class ThreadProcessor<TWorkItem> {
    private static final Logger LOG = Logger.getLogger(ThreadProcessor.class);

    public void processItems(final Iterator<TWorkItem> itemIterator, final int numThreads) throws UncaughtWorkerThreadException {
        TerminableThread workers[] = new TerminableThread[numThreads];
        final List<Throwable> uncaughtWorkerThreadException = new ArrayList<>();

        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread th, Throwable ex) {
                synchronized (uncaughtWorkerThreadException) {
                    uncaughtWorkerThreadException.add(ex);
                }
                for (TerminableThread wt : workers) {
                    wt.requestTermination();
                }
            }
        };

        for (int i = 0; i < numThreads; i++) {
            TerminableThread t = new TerminableThread() {
                @Override
                public void run() {
                    while (!terminationRequested) {
                        final TWorkItem nextItem;
                        synchronized (itemIterator) {
                            if (!itemIterator.hasNext()) {
                                break;
                            }
                            nextItem = itemIterator.next();
                        }

                        processItem(nextItem);
                    }

                    if (terminationRequested) {
                        LOG.info("Terminating thread " + this + ": termination requested.");
                    }
                }
            };
            t.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            workers[i] = t;
        }

        executeWorkers(workers);

        for (Throwable ex : uncaughtWorkerThreadException) {
            throw new UncaughtWorkerThreadException(ex);
        }
    }

    private void executeWorkers(Thread[] workers) {
        for (int iWorker = 0; iWorker < workers.length; iWorker++) {
            workers[iWorker].start();
        }

        for (int iWorker = 0; iWorker < workers.length; iWorker++) {
            try {
                workers[iWorker].join();
            } catch (InterruptedException e) {
                LOG.warn("Interrupted while waiting for change distance thread to finish.", e);
            }
        }
    }

    protected abstract void processItem(TWorkItem item);
}
