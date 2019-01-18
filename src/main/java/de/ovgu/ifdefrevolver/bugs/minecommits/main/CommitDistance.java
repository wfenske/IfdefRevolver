package de.ovgu.ifdefrevolver.bugs.minecommits.main;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDbCsvReader;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;

import java.io.PrintWriter;
import java.util.Optional;

public class CommitDistance {
    private static final Logger LOG = Logger.getLogger(CommitDistance.class);

    public static final String OPT_HELP = "h";
    public static final String OPT_DB = "d";
    public static final String OPT_CHILD_COMMIT = "c";
    public static final String OPT_ANCESTOR_COMMIT = "a";

    private CommitDistanceConfig conf;

    public static void main(String[] args) {
        CommitDistance me = new CommitDistance();
        me.doMain(args);
    }

    private static class CommitDistanceConfig {
        String dbName;
        String childCommit;
        String ancestorCommit;
    }

    protected void doMain(String[] args) {
        this.conf = parseCommandLine(args);
        try {
            LOG.debug("Populating DB from CSV file");
            CommitsDistanceDbCsvReader reader = new CommitsDistanceDbCsvReader();
            CommitsDistanceDb db = reader.dbFromCsv(conf.dbName);
            LOG.debug("Done populating DB from CSV file");
            LOG.debug("Pre-processing DB");
            db.ensurePreprocessed();
            LOG.debug("Done pre-processing DB");
            LOG.debug("Querying DB");
            long before = System.nanoTime();
//            for (int i = 0; i < 1000; i++) {
//                Optional<Integer> dist = db.minDistance(conf.childCommit, conf.ancestorCommit);
//            }

            Commit ancestorCommit = db.findCommitOrDie(conf.ancestorCommit);
            Commit childCommit = db.findCommitOrDie(conf.childCommit);


            Optional<Integer> dist = childCommit.distanceAmongCModifyingCommits(ancestorCommit);
            long after = System.nanoTime();
            LOG.debug("Done querying DB (" + (after - before) + "ns)");
            if (dist.isPresent()) {
                System.out.println(dist.get());
            } else {
                System.err.println("`" + conf.ancestorCommit + "' is not a known ancestor of `" + conf.childCommit + "'");
                System.err.flush();
                System.out.flush();
                System.exit(1);
            }
        } catch (Throwable t) {
            LOG.error("Internal error. See previous log messages for details.", t);
            System.err.flush();
            System.out.flush();
            System.exit(2);
        }
    }

    private CommitDistanceConfig parseCommandLine(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options fakeOptionsForHelp = makeOptions(true);
        Options actualOptions = makeOptions(false);

        CommandLine line;
        try {
            CommandLine dummyLine = parser.parse(fakeOptionsForHelp, args);
            if (dummyLine.hasOption('h')) {
                HelpFormatter formatter = new HelpFormatter();
                System.err.flush();
                formatter.printHelp(progName() + " [OPTIONS]",
                        getCommandLineHelpHeader(),
                        actualOptions, null, false);
                System.out.flush();
                System.exit(0);
                // We will never get here.
                return null;
            }
            line = parser.parse(actualOptions, args);
            if (!line.getArgList().isEmpty()) {
                throw new ParseException("No positional arguments expected, but got: " + line.getArgList());
            }
        } catch (ParseException e) {
            System.err.println("Error in command line: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printUsage(new PrintWriter(System.err, true), 80, progName(), actualOptions);
            System.exit(3);
            // We will never get here.
            return null;
        }

        CommitDistanceConfig conf = new CommitDistanceConfig();

        conf.dbName = line.getOptionValue(OPT_DB);
        conf.childCommit = line.getOptionValue(OPT_CHILD_COMMIT);
        conf.ancestorCommit = line.getOptionValue(OPT_ANCESTOR_COMMIT);

        return conf;
    }

    private Options makeOptions(boolean forHelp) {
        boolean required = !forHelp;
        Options options = new Options();
        //@formatter:off
        // --help= option
        options.addOption(Option.builder(OPT_HELP)
                .longOpt("help")
                .desc("print this help screen and exit")
                .build());
        // --database= option
        options.addOption(Option.builder(OPT_DB)
                .longOpt("database")
                .desc("Name of the CSV file that contains the information about the available commits and their parents.")
                .hasArg()
                .argName("FILE")
                .type(PatternOptionBuilder.FILE_VALUE)
                .required(required)
                .build());
        // --ancestor= option
        options.addOption(Option.builder(OPT_ANCESTOR_COMMIT)
                .longOpt("ancestor")
                .desc("Hash of the ancestor commit.")
                .hasArg()
                .argName("HASH")
                .type(PatternOptionBuilder.STRING_VALUE)
                .required(required)
                .build());
        // --child= option
        options.addOption(Option.builder(OPT_CHILD_COMMIT)
                .longOpt("child")
                .desc("Hash of the child commit.")
                .hasArg()
                .argName("HASH")
                .type(PatternOptionBuilder.STRING_VALUE)
                .required(required)
                .build());
        //@formatter:on
        return options;
    }

    private String progName() {
        return this.getClass().getSimpleName();
    }

    protected String getCommandLineHelpHeader() {
        return "Determine the minimum distance between two commits in terms of the number of commits.\n\nOptions:\n";
    }
}
