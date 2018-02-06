package de.ovgu.ifdefrevolver.bugs.minecommits.main;

import de.ovgu.ifdefrevolver.bugs.minecommits.AbstractSkunkCommitStudy;
import de.ovgu.ifdefrevolver.bugs.minecommits.Config;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.repodriller.RepoDriller;

import java.io.File;
import java.io.PrintWriter;

public abstract class AbstractSkunkCommitLister {
    private static final Logger LOG = Logger.getLogger(AbstractSkunkCommitLister.class);

    public static final String OPT_HELP = "h";
    public static final String OPT_OUTPUT_FILE = "o";
    public static final String OPT_REPO = "r";
    public static final String OPT_FORCE = "f";


    protected void doMain(String[] args) {
        Config conf = parseCommandLine(args);
        AbstractSkunkCommitStudy study = makeNewCommitStudy(conf);
        new RepoDriller().start(study);
        if (!study.wasStudySuccessful()) {
            LOG.error("Study was unsuccessful. See previous log messages for details. Output file "
                    + conf.outputFileName + " will be removed.");
            deleteOutputFileIfExists(conf);
            System.err.flush();
            System.out.flush();
            System.exit(1);
        }
    }

    private void deleteOutputFileIfExists(Config conf) {
        File outFile = new File(conf.outputFileName);
        boolean deleted = outFile.delete();
        if (!deleted && outFile.exists()) {
            LOG.warn("Failed to delete output file " + conf.outputFileName + ". Please delete it manually.");
        }
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
                formatter.printHelp(progName() + " [OPTIONS]",
                        getCommandLineHelpHeader(),
                        actualOptions, null, false);
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
        // --output= option
        options.addOption(Option.builder(OPT_OUTPUT_FILE)
                .longOpt("output")
                .desc(getOutputFileCommandLineOptionDescription())
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

    protected abstract AbstractSkunkCommitStudy makeNewCommitStudy(Config conf);

    protected String getOutputFileCommandLineOptionDescription() {
        return "output file, should be named `revisionsFull.csv' and be located in the project's results directory";
    }

    protected String getCommandLineHelpHeader() {
        return "List commits in a GIT repository, excluding merge commits and excluding commits that don't change .c files.\n\nOptions:\n";
    }
}
