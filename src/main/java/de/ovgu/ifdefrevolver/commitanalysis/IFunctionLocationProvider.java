package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.*;

public interface IFunctionLocationProvider {
    Set<String> C_FILE_EXTENSIONS = new HashSet<>(Arrays.asList(".c", ".C"));

    Map<String, List<Method>> listFunctionsInFiles(String commitId, RevCommit state, Set<String> paths) throws IOException;
}
