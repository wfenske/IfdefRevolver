package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.input.ProjectInformationReader;
import de.ovgu.ifdefrevolver.bugs.correlate.main.ProjectInformationConfig;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

public class ListChangedFunctions {
    private static final Logger LOG = Logger.getLogger(ListChangedFunctions.class);
    private ListChangedFunctionsConfig config;
    private int errors;

    public static void main(String[] args) {
        ListChangedFunctions main = new ListChangedFunctions();
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
        System.exit(main.errors);
    }

    private void execute() {
        LOG.debug("Listing changed functions in snapshots in " + config.projectSnapshotsDir() + " and repo " + config.getRepoDir());
        this.errors = 0;
        ProjectInformationReader<ListChangedFunctionsConfig> projectInfo = new ProjectInformationReader<>(config);
        LOG.debug("Reading project information");
        projectInfo.readSnapshotsAndRevisionsFile();
        LOG.debug("Done reading project information");
        Collection<Snapshot> snapshotsToProcess = projectInfo.getSnapshotsFiltered(config);
        listFunctionsInSnapshots(snapshotsToProcess);
    }

    private void listFunctionsInSnapshots(Collection<Snapshot> snapshots) {
        logSnapshotsToProcess(snapshots);

        final int totalSnapshots = snapshots.size();
        int numSnapshot = 1;
        for (final Snapshot s : snapshots) {
            LOG.info("Listing changed functions in snapshot " + (numSnapshot++) + "/" + totalSnapshots + ".");
            File resultCsv = listChangedFunctionsInSnapshot(s);
            LOG.info("Function changes saved in " + resultCsv.getAbsolutePath());
        }
        LOG.info("Done listing changed functions in " + totalSnapshots + " snapshots.");
        if (errors > 0) {
            LOG.warn("" + errors + " error(s) occurred.  See previous messages for details.");
        }
    }

    private void logSnapshotsToProcess(Collection<Snapshot> snapshotsToProcess) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("The following snapshots will be processed:");
            for (Snapshot s : snapshotsToProcess) {
                LOG.debug("" + s);
            }
        }
    }

    private File listChangedFunctionsInSnapshot(Snapshot snapshot) {
        LOG.debug("Listing functions changed in " + snapshot);
        SnapshotChangedFunctionLister lister = new SnapshotChangedFunctionLister(config, snapshot);
        File resultCsv = lister.listChangedFunctions();
        if (lister.errorsOccurred()) {
            errors++;
        }
        return resultCsv;
    }

    /*
    List<String> readCommitIdsFromStdin() {
        return readCommitIdsFromStream(System.in);
    }

    List<String> readCommitIdsFromStream(InputStream inputStream) {
        StringBuilder sb = new StringBuilder();
        final int bufSize = 1024;
        byte[] buf = new byte[bufSize];
        int read;
        while (true) {
            read = -1;
            try {
                read = inputStream.read(buf, 0, bufSize);
            } catch (IOException e) {
                // Whatever happened, we probably cannot read anything more.
                // So
                // just treat the situation as EOF.
                read = -1;
            }
            if (read == -1) {
                break;
            }
            String textChunk = new String(buf, 0, read);
            sb.append(textChunk);
        }
        List<String> commitIds = new ArrayList<>();
        for (String rawCommitId : sb.toString().split("\\s+")) {
            if (!rawCommitId.isEmpty()) {
                commitIds.add(rawCommitId);
            }
        }
        return commitIds;
    }
    */

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
            if (dummyLine.hasOption(ListChangedFunctionsConfig.OPT_HELP)) {
                System.out.flush();
                System.err.flush();
                HelpFormatter formatter = new HelpFormatter();
                //@formatter:off
                formatter.printHelp(progName()
                                + " [OPTION]... [SNAPSHOT]..."
                        , "List the signatures of the functions changed by the GIT commits in the snapshots of" +
                                " an IfdefRevolver project.  By default, all snapshots of the project will be" +
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
        this.config = new ListChangedFunctionsConfig();

        ProjectInformationConfig.parseProjectNameFromCommandLine(line, this.config);
        ProjectInformationConfig.parseProjectResultsDirFromCommandLine(line, this.config);
        ProjectInformationConfig.parseSnapshotsDirFromCommandLine(line, this.config);

        if (line.hasOption(ListChangedFunctionsConfig.OPT_REPO)) {
            config.setRepoDir(line.getOptionValue(ListChangedFunctionsConfig.OPT_REPO));
        } else {
            config.setRepoDir(Paths.get(ListChangedFunctionsConfig.DEFAULT_REPOS_DIR_NAME, this.config.getProject(), ".git").toString());
        }
        config.validateRepoDir();

        if (line.hasOption(ListChangedFunctionsConfig.OPT_THREADS)) {
            String threadsString = line.getOptionValue(ListChangedFunctionsConfig.OPT_THREADS);
            int numThreads;
            try {
                numThreads = Integer.valueOf(threadsString);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid value for option `-" + ListChangedFunctionsConfig.OPT_THREADS
                        + "': Not a valid integer: " + threadsString);
            }
            if (numThreads < 1) {
                throw new RuntimeException("Invalid value for option `-" + ListChangedFunctionsConfig.OPT_THREADS
                        + "': Number of threads must be an integer >= 1.");
            }
            config.setNumThreads(numThreads);
        }

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

        // --repo=foo/bar/.git GIT repository location
        options.addOption(Option.builder(String.valueOf(ListChangedFunctionsConfig.OPT_REPO))
                .longOpt(ListChangedFunctionsConfig.OPT_REPO_L)
                .desc("Directory containing the git repository to analyze." + " [Default="
                        + ListChangedFunctionsConfig.DEFAULT_REPOS_DIR_NAME + "/<project>/.git]")
                .hasArg().argName("DIR")
                //.required(required)
                .build());

        // --threads=1 options
        options.addOption(Option.builder(String.valueOf(ListChangedFunctionsConfig.OPT_THREADS))
                .longOpt(ListChangedFunctionsConfig.OPT_THREADS_L)
                .desc("Number of parallel analysis threads. Must be at least 1." + " [Default="
                        + ListChangedFunctionsConfig.DEFAULT_NUM_THREADS + "]")
                .hasArg().argName("NUM")
                .type(Integer.class)
                .build());

        // @formatter:on
        return options;
    }

    private String progName() {
        return this.getClass().getSimpleName();
    }
}
