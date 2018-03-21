package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EagerFunctionLocationProvider implements IFunctionLocationProvider {
    private final Repository repo;

    public EagerFunctionLocationProvider(Repository repo) {
        this.repo = repo;
    }

    @Override
    public Map<String, List<Method>> listFunctionsInFiles(String commitId, RevCommit state, Set<String> paths) throws IOException {
        if (paths.isEmpty()) {
            return Collections.emptyMap();
        }
        FunctionLocationProvider p = new FunctionLocationProvider(repo, commitId);
        return p.listFunctionsInFiles(state, paths);
    }
}
