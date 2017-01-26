package de.ovgu.skunk.commitanalysis.changedfunctions.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import de.ovgu.skunk.commitanalysis.changedfunctions.GitCommitChangedFunctionLister;

public class Main {
    private static final Logger LOG = Logger.getLogger(Main.class);
    private Config config;

    public static void main(String[] args) {
        Main main = new Main();
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
    }

    private void execute() {
        LOG.debug("Analyzing repo " + config.repoDir);
        LOG.debug("Listing functions changed by commits " + config.commitIds);
        if (config.commitIds.isEmpty()) {
            config.commitIds = readCommitIdsFromStdin();
        }
        GitCommitChangedFunctionLister lister = new GitCommitChangedFunctionLister(config);
        lister.run();
    }

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

    /**
     * Analyze input to decide what to do during runtime
     *
     * @param args
     *            the command line arguments
     */
    private void parseCommandLineArgs(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options fakeOptionsForHelp = makeOptions(true);
        Options actualOptions = makeOptions(false);
        CommandLine line;
        try {
            CommandLine dummyLine = parser.parse(fakeOptionsForHelp, args);
            if (dummyLine.hasOption(Config.OPT_HELP)) {
                System.out.flush();
                System.err.flush();
                HelpFormatter formatter = new HelpFormatter();
                //@formatter:off
                formatter.printHelp(progName()
                        + " [-" + Config.OPT_HELP + "]"
                        + " -" + Config.OPT_REPO + " DIR"
                        + " [COMMIT_ID ...]"
                        , "List the signatures of the functions changed by a GIT commit." /* header */
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
        this.config = new Config();
        config.repoDir = line.getOptionValue(Config.OPT_REPO);
        config.validateRepoDir();
        config.commitIds = line.getArgList();
    }

    private Options makeOptions(boolean forHelp) {
        boolean required = !forHelp;
        Options options = new Options();
        // @formatter:off

        // --help= option
        options.addOption(Option.builder(String.valueOf(Config.OPT_HELP))
                .longOpt(Config.OPT_HELP_L)
                .desc("print this help sceen and exit")
                .build());

        options.addOption(Option.builder(String.valueOf(Config.OPT_REPO))
                .longOpt(Config.OPT_REPO_L)
                .desc("directory containing the git repository to analyze")
                .hasArg().argName("DIR")
                .required(required)
                .build());

        // @formatter:on
        return options;
    }

    private String progName() {
        return this.getClass().getSimpleName();
    }
}
