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
import org.eclipse.jgit.treewalk.filter.OrTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
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
        TreeFilter pathFilter = PathFilterGroup.createFromStrings(paths);
        return listFunctionsInFiles(state, pathFilter);
    }

    /**
     * @param state
     * @param extensions A file extension, including the dot, i.e., stuff like <code>.c</code> for C files
     * @return A map from filename to the (ordered list of) functions in that file
     * @throws IOException
     */
    public Map<String, List<Method>> listFunctionsInFilesWithExtension(RevCommit state, Set<String> extensions) throws IOException {
        final TreeFilter effectiveFilter = treeFilterFromFileExtensions(extensions);
        return listFunctionsInFiles(state, effectiveFilter);
    }

    protected TreeFilter treeFilterFromFileExtensions(Set<String> extensions) {
        if (extensions.isEmpty()) {
            throw new IllegalArgumentException("Must supply at least one file extension!");
        }
        PathSuffixFilter[] filters = new PathSuffixFilter[extensions.size()];
        int i = 0;
        for (String ext : extensions) {
            filters[i++] = PathSuffixFilter.create(ext);
        }
        final TreeFilter effectiveFilter;
        if (filters.length == 1) effectiveFilter = filters[0];
        else effectiveFilter = OrTreeFilter.create(filters);
        return effectiveFilter;
    }

    private Map<String, List<Method>> listFunctionsInFiles(RevCommit stateBeforeCommit, TreeFilter pathFilter) throws IOException {
        final Map<String, List<Method>> functionsByFilename = new HashMap<>();
        Consumer<Method> changedFunctionHandler = new Consumer<Method>() {
            @Override
            public void accept(Method method) {
                String filePath = method.filePath;
                List<Method> functions = functionsByFilename.get(filePath);
                if (functions == null) {
                    functions = new ArrayList<>();
                    functionsByFilename.put(filePath, functions);
                    functions.add(method);
                } else {
                    Method previousMethod = functions.get(functions.size() - 1);
                    previousMethod.maybeAdjustMethodEndBasedOnNextFunction(method);
                    functions.add(method);
                }
            }
        };

        listFunctionsInFiles(stateBeforeCommit, pathFilter, changedFunctionHandler);
        return functionsByFilename;
    }

    private void listFunctionsInFiles(RevCommit stateBeforeCommit, TreeFilter pathFilter, Consumer<Method> functionHandler) throws IOException {
        // a RevWalk allows to walk over commits based on some filtering that is defined
        // and using commit's tree find the path
        RevTree tree = stateBeforeCommit.getTree();
        LOG.debug("Analyzing state " + stateBeforeCommit.getId().name() + ". Looking at tree: " + tree);
        //LOG.debug("Paths to analyze: " + paths);

        // now try to find a specific file
        TreeWalk treeWalk = null;
        try {
            treeWalk = new TreeWalk(repository);
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(pathFilter);

            //final Set<String> filesToGo = new HashSet<>(paths);
            while (treeWalk.next()) {
                ObjectId objectId = treeWalk.getObjectId(0);
                String path = treeWalk.getPathString();
//                if (!filesToGo.remove(path)) {
//                    throw new IllegalStateException("Unexpected file in commit " + commitId + ". Expected one of " +
//                            filesToGo + ", got: " + path);
//                }
                ObjectLoader loader = repository.open(objectId);
                //System.out.print(getSrcMl(loader));
                readFileForPath(loader, path, functionHandler);
            }

//            if (!filesToGo.isEmpty()) {
//                throw new IllegalStateException("Did not find the following files in commit " + commitId + ": " + filesToGo);
//            }
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
        LOG.debug("Getting SrcML of " + path + " at " + commitId);
        Document[] doc = new Document[1];
        getSrcMlStdoutStream(loader, new Consumer<InputStream>() {
            @Override
            public void accept(InputStream procStdout) {
                /*
                final int len = 100_000_000;
                byte[] buffer = new byte[len];
                int read = 0;
                while (true) {
                    try {
                        int readLocally = procStdout.read(buffer, read, len - read);
                        if (readLocally == -1) break;
                        read += readLocally;
                    } catch (IOException ioe) {
                        LOG.warn("Error reading " + path + " at " + commitId, ioe);
                    }
                }

                String s = "";
                try {
                    s = new String(buffer, 0, read, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    LOG.warn("Error reading " + path + " at " + commitId, e);
                }
                LOG.debug("XXX File " + path + " at " + commitId + "\n" + s);

                procStdout = new ByteArrayInputStream(buffer, 0, read);
                */

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
