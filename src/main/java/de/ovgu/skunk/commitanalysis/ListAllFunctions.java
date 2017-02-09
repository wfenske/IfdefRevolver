package de.ovgu.skunk.commitanalysis;

import de.ovgu.skunk.bugs.correlate.main.Config;
import de.ovgu.skunk.bugs.createsnapshots.main.CreateSnapshots;
import de.ovgu.skunk.detection.data.Context;
import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.detection.input.SrcMlFolderReader;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ListAllFunctions {
    private static final char OPT_HELP = 'h';
    private static final String OPT_HELP_L = "help";

    /**
     * Name of the project to analyze
     */
    private static final char OPT_PROJECT = 'p';

    /**
     * Long name of the {@link #OPT_PROJECT} option.
     */
    private static final String OPT_PROJECT_L = "project";

    /**
     * Directory containing the &lt;project&gt;ABRes/*.csv,
     * &lt;project&gt;AFRes/*.csv, &lt;project&gt;LFRes/*.csv files
     */
    //private static final String OPT_RESULTS_DIR_L = "resultsdir";
    private static final String OPT_SNAPSHOTS_DIR_L = "snapshotsdir";


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
        //LOG.debug("Analyzing snapshots in " + config.projectSnapshotsDir());
        this.errors = 0;
        listFunctionsInFiles(config.filenames);
    }

    private void listFunctionsInFiles(List<String> filenames) {
        int ixFile = 1;
        final int numFiles = filenames.size();
        for (String filename : filenames) {
            try {
                LOG.info("Processing file " + (ixFile++) + "/" + numFiles);
                listFunctions(filename);
            } catch (RuntimeException t) {
                LOG.warn("Error processing file " + filename, t);
                errors++;
            }
        }
    }

    private void listFunctions(String filename) {
        Context ctx = new Context(null);
        SrcMlFolderReader folderReader = new SrcMlFolderReader(ctx);
        Document doc = folderReader.readAndRememberSrcmlFile(filename);
        NodeList functions = doc.getElementsByTagName("function");
        int numFunctions = functions.getLength();
        LOG.debug("Found " + numFunctions + " functions in `" + filename + "'.");
        List<Method> functionsInFile = new ArrayList<>();
        for (int i = 0; i < numFunctions; i++) {
            Node funcNode = functions.item(i);
            Method func = folderReader.parseFunction(funcNode, filename);
            functionsInFile.add(func);
        }

        for (Method func : functionsInFile) {
            System.out.println(func.FilePathForDisplay() + "\t" + func.functionSignatureXml);
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
            if (dummyLine.hasOption(OPT_HELP)) {
                System.out.flush();
                System.err.flush();
                HelpFormatter formatter = new HelpFormatter();
                //@formatter:off
                formatter.printHelp(progName()
                                + " [OPTIONS]"
                        , "List functions defined in the C files of an IfdefRevolver snapshot. The snapshot is expected to have been created by the " +
                                CreateSnapshots.class.getSimpleName() + " tool." /* header */
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

        LOG.trace("line=" + line.getArgList());

        this.config = new ListAllFunctionsConfig();

        final String project = line.getOptionValue(OPT_PROJECT);
        this.config.project = project;

        final String snapshotsDirName;
        if (line.hasOption(OPT_SNAPSHOTS_DIR_L)) {
            snapshotsDirName = line.getOptionValue(OPT_SNAPSHOTS_DIR_L);
        } else {
            snapshotsDirName = Config.DEFAULT_SNAPSHOTS_DIR_NAME;
        }
        final File snapshotsDir = new File(snapshotsDirName);
        final File projectSnapshotsDir = new File(snapshotsDir, project);
        if (!projectSnapshotsDir.exists() || !projectSnapshotsDir.isDirectory()) {
            throw new RuntimeException(
                    "The project's snapshots directory does not exist or is not a directory: "
                            + projectSnapshotsDir.getAbsolutePath());
        }
        this.config.snapshotsDir = snapshotsDir.getAbsolutePath();

        this.config.filenames = line.getArgList();
    }

    private Options makeOptions(boolean forHelp) {
        boolean required = !forHelp;
        Options options = new Options();
        // @formatter:off

        // --help= option
        options.addOption(Option.builder(String.valueOf(ListChangedFunctionsConfig.OPT_HELP))
                .longOpt(ListChangedFunctionsConfig.OPT_HELP_L)
                .desc("print this help sceen and exit")
                .build());

        options.addOption(Option.builder(String.valueOf(OPT_PROJECT)).longOpt(OPT_PROJECT_L)
                .desc("Name of the project to analyze. The project's data must be located in subdirectories of"
                        + " the results and snapshot directories.")
                .hasArg().argName("NAME").required(required).build());

        options.addOption(Option.builder().longOpt(OPT_SNAPSHOTS_DIR_L)
                .desc("Directory where snapshots are located. The project's snapshots must be located in the "
                        + "<project> subdirectory within this directory, where <project>"
                        + " is the project name specified via the `--" + OPT_PROJECT_L + "' option." + " [Default="
                        + Config.DEFAULT_SNAPSHOTS_DIR_NAME + "]")
                .hasArg().argName("DIR").type(PatternOptionBuilder.EXISTING_FILE_VALUE)
                .build());

        // @formatter:on
        return options;
    }

    private String progName() {
        return this.getClass().getSimpleName();
    }
}
