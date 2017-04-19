package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.input.ProjectInformationReader;
import de.ovgu.ifdefrevolver.bugs.correlate.main.ProjectInformationConfig;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.main.CreateSnapshots;
import de.ovgu.skunk.detection.data.Context;
import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.detection.input.SrcMlFolderReader;
import de.ovgu.skunk.detection.output.CsvFileWriterHelper;
import de.ovgu.skunk.detection.output.CsvRowProvider;
import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class ListAllFunctions {
    private static final Logger LOG = Logger.getLogger(ListAllFunctions.class);
    private ListAllFunctionsConfig config;
    private int errors;

    public static void main(String[] args) {
        ListAllFunctions main = new ListAllFunctions();
        try {
            main.parseCommandLineArgs(args);
        } catch (Exception e) {
            System.err.println("Error while processing command line arguments: " + e);
            e.printStackTrace();
            System.err.flush();
            System.out.flush();
            System.exit(1);
        }
        main.execute();
        if (main.errors != 0) {
            System.err.println("Some files could not be processed. See previous log messages.");
            System.err.flush();
            System.out.flush();
            System.exit(1);
        }
    }

    private void execute() {
        LOG.debug("Listing all function definitions in snapshots in " + config.projectSnapshotsDir());
        this.errors = 0;
        ProjectInformationReader<ListAllFunctionsConfig> projectInfo = new ProjectInformationReader<>(config);
        LOG.debug("Reading project information");
        projectInfo.readSnapshotsAndRevisionsFile();
        LOG.debug("Done reading project information");
        Collection<Snapshot> snapshotsToProcess = projectInfo.getSnapshotsFiltered(config);
        listFunctionsInSnapshots(snapshotsToProcess);
    }

    private void listFunctionsInSnapshots(Collection<Snapshot> snapshots) {
        final int totalSnapshots = snapshots.size();
        int numSnapshot = 1;
        for (final Snapshot s : snapshots) {
            LOG.info("Listing functions in snapshot " + (numSnapshot++) + "/" + totalSnapshots + ".");
            File outputFileDir = config.snapshotResultsDirForDate(s.getSnapshotDate());
            File outputFile = new File(outputFileDir, AllSnapshotFunctionsColumns.FILE_BASENAME);
            CsvFileWriterHelper helper = newCsvFileWriterForSnapshot(s, outputFile);
            helper.write(outputFile);
        }
        LOG.info("Done listing functions in " + totalSnapshots + " snapshots.");
    }

    private CsvFileWriterHelper newCsvFileWriterForSnapshot(final Snapshot snapshot, File outputFile) {
        final String uncaughtExceptionErrorMessage = "Uncaught exception while listing all functions in snapshot " + snapshot + ". Deleting output file " + outputFile.getAbsolutePath();
        final String fileDeleteFailedErrorMessage = "Failed to delete output file " + outputFile.getAbsolutePath() + ". Must be deleted manually.";
        return new CsvFileWriterHelper() {
            CsvRowProvider<Method, Snapshot, AllSnapshotFunctionsColumns> csvRowProvider = AllSnapshotFunctionsColumns.newCsvRowProviderForSnapshot(snapshot);

            @Override
            protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
                csv.printRecord(csvRowProvider.headerRow());
                Consumer<Method> csvRowFromFunction = newThreadSafeFunctionToCsvWriter(csv, csvRowProvider);
                try {
                    listFunctionsInSnapshot(snapshot, csvRowFromFunction);
                } catch (UncaughtWorkerThreadException ex) {
                    increaseErrorCount();
                    LOG.error(uncaughtExceptionErrorMessage, ex);
                    if (outputFile.delete()) {
                    } else {
                        LOG.error(fileDeleteFailedErrorMessage);
                    }
                }
            }
        };
    }

    private Consumer<Method> newThreadSafeFunctionToCsvWriter(final CSVPrinter csv, final CsvRowProvider<Method, Snapshot, AllSnapshotFunctionsColumns> csvRowProvider) {
        return new Consumer<Method>() {
            @Override
            public void accept(Method function) {
                Object[] rowForFunc = csvRowProvider.dataRow(function);
                try {
                    synchronized (csv) {
                        csv.printRecord(rowForFunc);
                    }
                } catch (IOException ioe) {
                    throw new RuntimeException("IOException while writing row for function " +
                            function, ioe);
                }
            }
        };
    }

    private void listFunctionsInSnapshot(final Snapshot snapshot, Consumer<Method> functionDefinitionsConsumer) throws UncaughtWorkerThreadException {
        LOG.debug("Listing all functions in " + snapshot);
        List<File> cFilesInSnapshot = snapshot.listSrcmlCFiles();
        Collection<String> filenames = filenamesFromFiles(cFilesInSnapshot);
        listFunctionsInFilesByFilename(filenames, functionDefinitionsConsumer);
    }

    private static Collection<String> filenamesFromFiles(Collection<File> files) {
        Collection<String> filenames = new ArrayList<>(files.size());
        for (File f : files) {
            filenames.add(f.getAbsolutePath());
        }
        return filenames;
    }

    private void listFunctionsInFilesByFilename(Collection<String> filenames, Consumer<Method> functionDefinitionsConsumer) throws UncaughtWorkerThreadException {
        final Iterator<String> filenameIter = filenames.iterator();

        final int NUM_FUNCTION_LISTING_WORKER_THREADS = 4;
        final TerminableThread[] workers = new TerminableThread[NUM_FUNCTION_LISTING_WORKER_THREADS];
        final List<Throwable> uncaughtWorkerThreadException = new ArrayList<>(NUM_FUNCTION_LISTING_WORKER_THREADS);

        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread th, Throwable ex) {
                increaseErrorCount();
                for (TerminableThread wt : workers) {
                    wt.requestTermination();
                }
                synchronized (uncaughtWorkerThreadException) {
                    uncaughtWorkerThreadException.add(ex);
                }
            }
        };

        for (int iWorker = 0; iWorker < workers.length; iWorker++) {
            TerminableThread t = new TerminableThread() {
                @Override
                public void run() {
                    while (!terminationRequested) {
                        final String nextFilename;
                        synchronized (filenameIter) {
                            if (!filenameIter.hasNext()) {
                                break;
                            }
                            nextFilename = filenameIter.next();
                        }
                        try {
                            //LOG.info("Processing file " + (ixFile++) + "/" + numFiles);
                            listFunctions(nextFilename, functionDefinitionsConsumer);
                        } catch (RuntimeException t) {
                            LOG.warn("Error processing file " + nextFilename, t);
                            increaseErrorCount();
                        }
                    }

                    if (terminationRequested) {
                        LOG.info("Terminating thread " + this + ": termination requested.");
                    }
                }
            };
            t.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            workers[iWorker] = t;
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
                LOG.warn("Interrupted while waiting for function lister thread to finish.", e);
            }
        }
    }

    private synchronized void increaseErrorCount() {
        errors++;
    }

    private void listFunctions(String filename, Consumer<Method> functionDefinitionsConsumer) {
        Context ctx = new Context(null);
        SrcMlFolderReader folderReader = new SrcMlFolderReader(ctx);
        Document doc = folderReader.readAndRememberSrcmlFile(filename);
        NodeList functions = doc.getElementsByTagName("function");
        int numFunctions = functions.getLength();
        LOG.debug("Found " + numFunctions + " functions in `" + filename + "'.");
        for (int i = 0; i < numFunctions; i++) {
            Node funcNode = functions.item(i);
            Method func = folderReader.parseFunction(funcNode, filename);
            functionDefinitionsConsumer.accept(func);
        }
    }

    /**
     * Analyze input to decide what to do during runtime
     *
     * @param args the command line arguments
     */
    private void parseCommandLineArgs(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options fakeOptionsForHelp = makeOptions(true);
        Options actualOptions = makeOptions(false);
        CommandLine line;
        try {
            CommandLine dummyLine = parser.parse(fakeOptionsForHelp, args);
            if (dummyLine.hasOption(ProjectInformationConfig.OPT_HELP)) {
                System.out.flush();
                System.err.flush();
                HelpFormatter formatter = new HelpFormatter();
                //@formatter:off
                formatter.printHelp(progName()
                                + " [OPTION]... [SNAPSHOT]..."
                        , "List all functions defined in the C files of an IfdefRevolver project." +
                                "  The project is expected to have been created by the " +
                                CreateSnapshots.class.getSimpleName() + " tool." +
                                "  By default, all snapshots of the project will be" +
                                " analyzed.  If you wish to analyze only specific snapshots, you can list their dates" +
                                " in YYYY-MM-DD format after the last named command line option." /* header */
                        , actualOptions
                        , null /* footer */
                );
                //@formatter:on
                System.out.flush();
                System.err.flush();
                System.exit(0);
                // We never actually get here due to the preceding
                // System.exit(int) call.
                return;
            }
            line = parser.parse(actualOptions, args);
        } catch (ParseException e) {
            System.out.flush();
            System.err.flush();
            System.err.println("Error in command line: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printUsage(new PrintWriter(System.err, true), 80, progName(), actualOptions);
            System.out.flush();
            System.err.flush();
            System.exit(1);
            // We never actually get here due to the preceding System.exit(int)
            // call.
            return;
        }

        this.config = new ListAllFunctionsConfig();

        ProjectInformationConfig.parseProjectNameFromCommandLine(line, this.config);

        ProjectInformationConfig.parseProjectResultsDirFromCommandLine(line, this.config);

        ProjectInformationConfig.parseSnapshotsDirFromCommandLine(line, this.config);

        List<String> snapshotDateNames = line.getArgList();
        if (!snapshotDateNames.isEmpty()) {
            ListChangedFunctionsConfig.parseSnapshotFilterDates(snapshotDateNames, config);
        }
    }

    private Options makeOptions(boolean forHelp) {
        boolean required = !forHelp;
        Options options = new Options();
        // @formatter:off

        // --help= option
        options.addOption(ProjectInformationConfig.helpCommandLineOption());

        // Options for describing project locations
        options.addOption(ProjectInformationConfig.projectNameCommandLineOption(required));
        options.addOption(ProjectInformationConfig.resultsDirCommandLineOption());
        options.addOption(ProjectInformationConfig.snapshotsDirCommandLineOption());

        // @formatter:on
        return options;
    }

    private String progName() {
        return this.getClass().getSimpleName();
    }
}
