package de.ovgu.skunk.bugs.miner.main;

import java.io.PrintWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PatternOptionBuilder;

import de.ovgu.skunk.bugs.miner.BugfixCommitStudy;
import de.ovgu.skunk.bugs.miner.Config;
import org.repodriller.RepoDriller;

public class FindBugfixCommits {

    public static final String OPT_HELP = "h";
    public static final String OPT_OUTPUT_FILE = "o";
    public static final String OPT_KEYWORDS = "k";
    public static final String OPT_REPO = "r";
    public static final String OPT_FORCE = "f";

    public static void main(String[] args) {

        // analyze args input
        FindBugfixCommits me = new FindBugfixCommits();
        Config conf = me.parseCommandLine(args);

        BugfixCommitStudy study = new BugfixCommitStudy(conf);
        new RepoDriller().start(study);
    }

    private Config parseCommandLine(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options fakeOptionsForHelp = makeOptions(true);
        Options actualOptions = makeOptions(false);

        CommandLine line;
        try {
            CommandLine dummyLine = parser.parse(fakeOptionsForHelp, args);
            if (dummyLine.hasOption('h')) {
                HelpFormatter formatter = new HelpFormatter();
                System.err.flush();
                formatter.printHelp(progName() + " [OPTIONS]", actualOptions);
                System.out.flush();
                System.exit(0);
                // We will never get here.
                return null;
            }
            line = parser.parse(actualOptions, args);
        } catch (ParseException e) {
            System.err.println("Error in command line: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printUsage(new PrintWriter(System.err, true), 80, progName(), actualOptions);
            System.exit(1);
            // We will never get here.
            return null;
        }

        Config conf = new Config();

        conf.repoPathName = line.getOptionValue(OPT_REPO);
        final String keywords;
        if (line.hasOption(OPT_KEYWORDS)) {
            keywords = line.getOptionValue(OPT_KEYWORDS);
        } else {
            keywords = Config.DEFAULT_BUGFIX_TERMS;
        }
        conf.bugfixTerms = keywords.split(",");
        conf.outputFileName = line.getOptionValue(OPT_OUTPUT_FILE);
        if (line.hasOption(OPT_FORCE)) {
            conf.forceOverwriteOutput = true;
        }

        return conf;
    }

    private Options makeOptions(boolean forHelp) {
        boolean required = !forHelp;
        Options options = new Options();
        //@formatter:off
        // --help= option
        options.addOption(Option.builder(OPT_HELP)
                .longOpt("help")
                .desc("print this help sceen and exit")
                .build());
        // --repo= option
        options.addOption(Option.builder(OPT_REPO)
                .longOpt("repo")
                .desc("path to GIT repository")
                .hasArg()
                .argName("DIR")
                .type(PatternOptionBuilder.EXISTING_FILE_VALUE)
                .required(required)
                .build());
        // --keywords= option
        options.addOption(Option.builder(OPT_KEYWORDS)
                .longOpt("keywords")
                .desc("comma-separated list of keywords to identify bug-fix commits [default="
                        + Config.DEFAULT_BUGFIX_TERMS + "]")
                .hasArg()
                .argName("WORD[,WORD[,...]]")
                //.required(required)
                .build());
        // --output= option
        options.addOption(Option.builder(OPT_OUTPUT_FILE)
                .longOpt("output")
                .desc("output file, should be named `revisionsFull.csv' and be located"
                        + " in the project's results directory")
                .hasArg()
                .argName("FILE")
                .type(PatternOptionBuilder.FILE_VALUE)
                .required(required)
                .build());
        // --force= option
        options.addOption(Option.builder(OPT_FORCE)
                .longOpt("force")
                .desc("force overwriting the output file if it already exists")
                .build());
        //@formatter:on
        return options;
    }

    private String progName() {
        return this.getClass().getSimpleName();
    }
}
