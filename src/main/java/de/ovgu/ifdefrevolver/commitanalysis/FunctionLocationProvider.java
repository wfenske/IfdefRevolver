package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Context;
import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.detection.input.SrcMlFolderReader;
import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Created by wfenske on 06.12.16.
 */
public class FunctionLocationProvider {
    private static final Logger LOG = Logger.getLogger(FunctionLocationProvider.class);
    private final Context ctx;
    private final SrcMlFolderReader folderReader;
    private final Repository repository;
    private final String commitId;

    public FunctionLocationProvider(Repository repository, String commitId) throws IOException {
        this.repository = repository;
        this.commitId = commitId;
        this.ctx = new Context(null);
        this.folderReader = new SrcMlFolderReader(ctx);
    }

    /**
     * @param state
     * @param paths
     * @return A map from filename to the (ordered list of) functions in that file
     * @throws IOException
     */
    public Map<String, List<Method>> listFunctionsInFiles(RevCommit state, Set<String> paths) throws IOException {
        final Map<String, List<Method>> functionsByFilename = new HashMap<>();
        Consumer<Method> changedFunctionHandler = method -> {
            String filePath = method.filePath;
            List<Method> functions = functionsByFilename.get(filePath);
            if (functions == null) {
                functions = new ArrayList<>();
                functionsByFilename.put(filePath, functions);
            }
            functions.add(method);
        };
        listFunctionsInFiles(state, paths, changedFunctionHandler);
        return functionsByFilename;
    }

    private void listFunctionsInFiles(RevCommit stateBeforeCommit, Set<String> paths, Consumer<Method> functionHandler) throws IOException {
        // a RevWalk allows to walk over commits based on some filtering that is defined
        // and using commit's tree find the path
        RevTree tree = stateBeforeCommit.getTree();
        LOG.debug("Analyzing state " + stateBeforeCommit.getId().name() + ". Looking at tree: " + tree);
        LOG.debug("Paths to analyze: " + paths);

        // now try to find a specific file
        TreeWalk treeWalk = null;
        try {
            treeWalk = new TreeWalk(repository);
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            TreeFilter pathFilter = PathFilterGroup.createFromStrings(paths);
            treeWalk.setFilter(pathFilter);

            final Set<String> filesToGo = new HashSet<>(paths);
            while (treeWalk.next()) {
                ObjectId objectId = treeWalk.getObjectId(0);
                String path = treeWalk.getPathString();
                if (!filesToGo.remove(path)) {
                    throw new IllegalStateException("Unexpected file in commit " + commitId + ". Expected one of " +
                            filesToGo + ", got: " + path);
                }
                ObjectLoader loader = repository.open(objectId);
                //System.out.print(getSrcMl(loader));
                readFileForPath(loader, path, functionHandler);
            }

            if (!filesToGo.isEmpty()) {
                throw new IllegalStateException("Did not find the following files in commit " + commitId + ": " + filesToGo);
            }
        } finally {
            treeWalk.release();
        }
    }

    private void readFileForPath(ObjectLoader loader, String filePath, Consumer<Method> functionHandler) {
        LOG.debug("Parsing functions in " + filePath);
        Document doc = getSrcMlDoc(loader, filePath);
        NodeList functions = doc.getElementsByTagName("function");
        int numFunctions = functions.getLength();
        LOG.debug("Found " + numFunctions + " functions in `" + filePath + "'.");
        for (int i = 0; i < numFunctions; i++) {
            Node funcNode = functions.item(i);
            Method func = folderReader.parseFunction(funcNode, filePath);
            //LOG.debug("\t" + func.toString());
            functionHandler.accept(func);
        }
    }

    private String getSrcMl(ObjectLoader loader) {
        final StringBuilder resBuilder = new StringBuilder();
        getSrcMlByLine(loader, new Consumer<String>() {
            @Override
            public void accept(String line) {
                resBuilder.append(line);
                resBuilder.append("\n");
            }
        });
        return resBuilder.toString();
    }


    private Document getSrcMlDoc(ObjectLoader loader, String path) {
        LOG.debug("Getting SrcML of " + path);
        Document[] doc = new Document[1];
        getSrcMlStdoutStream(loader, new Consumer<InputStream>() {
            @Override
            public void accept(InputStream procStdout) {
                doc[0] = folderReader.readAndRememberSrcmlFile(procStdout, path);
            }
        });
        return doc[0];
    }

    private void getSrcMlByLine(ObjectLoader loader, Consumer<String> srcmlLineConsumer) {
        getSrcMlStdoutStream(loader, new Consumer<InputStream>() {
            @Override
            public void accept(InputStream procStdout) {
                try (BufferedReader stdOutReader = new BufferedReader(new InputStreamReader(procStdout))) {
                    stdOutReader.lines().forEach(srcmlLineConsumer);
                } catch (IOException e) {
                    throw new RuntimeException("I/O error reading src2srcml output", e);
                }
            }
        });
    }

    private void getSrcMlStdoutStream(ObjectLoader loader, final Consumer<InputStream> srcmlStdoutConsumer) {
        ProcessBuilder builder = new ProcessBuilder("src2srcml", "-lC");
        builder.inheritIO()
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectInput(ProcessBuilder.Redirect.PIPE);
        Process process;

        try {
            process = builder.start();
        } catch (IOException e) {
            throw new RuntimeException("I/O error starting src2srcml", e);
        }

        try (final InputStream procStdout = process.getInputStream()) {
            final Process theProc = process;

            Thread stdinWriter = new Thread() {
                @Override
                public void run() {
                    try (OutputStream procStdin = theProc.getOutputStream()) {
                        loader.copyTo(procStdin);
                        procStdin.flush();
                    } catch (IOException e) {
                        throw new RuntimeException("I/O error piping C source code to src2srml", e);
                    }
                }
            };

            stdinWriter.start();
            srcmlStdoutConsumer.accept(procStdout);

            try {
                process.waitFor();
            } catch (InterruptedException e) {
                throw new RuntimeException("Error while waiting for src2srcml to finish", e);
            }

            try {
                stdinWriter.join();
            } catch (InterruptedException e) {
                throw new RuntimeException("Error while waiting for thread writing to src2srcml to finish", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("I/O error reading src2srcml output", e);
        }
    }
}
