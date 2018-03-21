package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IFunctionLocationProvider {
    Map<String, List<Method>> listFunctionsInFiles(String commitId, RevCommit state, Set<String> paths) throws IOException;
}
