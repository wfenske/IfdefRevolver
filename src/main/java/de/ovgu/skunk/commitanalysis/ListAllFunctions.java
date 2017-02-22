package de.ovgu.skunk.commitanalysis;

import de.ovgu.skunk.bugs.correlate.data.Snapshot;
import de.ovgu.skunk.bugs.correlate.input.ProjectInformationReader;
import de.ovgu.skunk.bugs.correlate.main.ProjectInformationConfig;
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
import java.util.*;
import java.util.function.Consumer;

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
        LOG.debug("Analyzing snapshots in " + config.projectSnapshotsDir());
        this.errors = 0;
        //listFunctionsInFiles(config.filenames);
        ProjectInformationReader<ListAllFunctionsConfig> projectInfo = new ProjectInformationReader<>(config);
        LOG.debug("Reading project information");
        projectInfo.readSnapshotsAndRevisionsFile();
        LOG.debug("Done reading project information");
        SortedMap<Date, Snapshot> snapshots = projectInfo.getSnapshots();
        listFunctionsInSnapshots(snapshots.values());
    }

    private void listFunctionsInSnapshots(Collection<Snapshot> snapshots) {
        for (Snapshot s : snapshots) {
            listFunctionsInSnapshot(s, SIMPLE_FUNCTION_DEFINITION_LISTER);
        }
    }

    private void listFunctionsInSnapshot(Snapshot snapshot, Consumer<Method> functionDefinitionsConsumer) {
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

    private void listFunctionsInFilesByFilename(Collection<String> filenames, Consumer<Method> functionDefinitionsConsumer) {
        int ixFile = 1;
        final int numFiles = filenames.size();
        for (String filename : filenames) {
            try {
                LOG.info("Processing file " + (ixFile++) + "/" + numFiles);
                listFunctions(filename, functionDefinitionsConsumer);
            } catch (RuntimeException t) {
                LOG.warn("Error processing file " + filename, t);
                errors++;
            }
        }
    }

    private static final Consumer<Method> SIMPLE_FUNCTION_DEFINITION_LISTER = new Consumer<Method>() {
        @Override
        public void accept(Method func) {
            System.out.println(func.FilePathForDisplay() + "\t" + func.functionSignatureXml);
        }
    };

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

        this.config = new ListAllFunctionsConfig();

        ProjectInformationConfig.parseProjectNameFromCommandLine(line, this.config);

        ProjectInformationConfig.parseProjectResultsDirFromCommandLine(line, this.config);

        ProjectInformationConfig.parseSnapshotsDirFromCommandLine(line, this.config);
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
