package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import org.apache.log4j.Logger;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.*;

public class CachingFunctionLocationProvider implements IFunctionLocationProvider {
    private static final Logger LOG = Logger.getLogger(CachingFunctionLocationProvider.class);

    private static class CacheKey {
        final RevCommit state;
        final String path;

        public CacheKey(RevCommit state, String path) {
            this.state = state;
            this.path = path;
        }

        @Override
        public int hashCode() {
            return state.hashCode() ^ path.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (!(obj instanceof CacheKey)) return false;
            CacheKey other = (CacheKey) obj;
            return this.state.equals(other.state) && this.path.equals(other.path);
        }
    }

    private final IFunctionLocationProvider parentProvider;
    private Map<CacheKey, List<Method>> cache = new HashMap<>();

    public CachingFunctionLocationProvider(IFunctionLocationProvider parentProvider) {
        this.parentProvider = parentProvider;
    }

    @Override
    public Map<String, List<Method>> listFunctionsInFiles(String commitId, final RevCommit state, Set<String> paths) throws IOException {
        Map<String, List<Method>> results = new HashMap<>();
        Set<String> missingPaths = new HashSet<>();

        for (String path : paths) {
            CacheKey key = new CacheKey(state, path);
            List<Method> functions = cache.get(key);
            if (functions == null) {
                missingPaths.add(path);
            } else {
                results.put(path, functions);
            }
        }

        LOG.debug("Found " + results.size() + " file(s) in cache.");

        if (!missingPaths.isEmpty()) {
            LOG.debug("Parsing " + missingPaths.size() + " new file(s).");
            Map<String, List<Method>> missingResults = parentProvider.listFunctionsInFiles(commitId, state, missingPaths);
            cacheMissingResults(state, missingResults);

            if (results.isEmpty()) {
                results = missingResults;
            } else {
                results.putAll(missingResults);
            }
        }

        return results;
    }

    private void cacheMissingResults(RevCommit state, Map<String, List<Method>> missingResults) {
        for (Map.Entry<String, List<Method>> e : missingResults.entrySet()) {
            String missingPath = e.getKey();
            List<Method> missingFunctions = e.getValue();
            CacheKey key = new CacheKey(state, missingPath);
            cache.put(key, missingFunctions);
        }
    }
}
