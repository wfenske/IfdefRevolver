package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.util.GroupingListMap;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Consumes change hunks of different types. In case a function is deleted and subsequently moved, the two edits are
 * aggregated to form a function move change.  Other changes are passed on as is.
 * <p>
 * Created by wfenske on 2018-03-21.
 */
public class AddDelMergingConsumer implements Consumer<FunctionChangeHunk> {
    private static final Logger LOG = Logger.getLogger(AddDelMergingConsumer.class);

    private Map<String, LinkedList<FunctionChangeHunk>> delsByFunctionName = new LinkedHashMap<>();
    private Map<String, LinkedList<FunctionChangeHunk>> addsByFunctionName = new LinkedHashMap<>();
    private List<FunctionChangeHunk> rememberedMoves = new ArrayList<>();
    private GroupingListMap<Method, FunctionChangeHunk> retainedMods = new GroupingListMap<>();

    private final Consumer<FunctionChangeHunk> parent;

    public AddDelMergingConsumer(Consumer<FunctionChangeHunk> parent) {
        this.parent = parent;
    }

    @Override
    public void accept(FunctionChangeHunk functionChangeHunk) {
        switch (functionChangeHunk.getModType()) {
            case ADD:
                retainAddOrDelHunk(addsByFunctionName, functionChangeHunk);
                break;
            case DEL:
                retainAddOrDelHunk(delsByFunctionName, functionChangeHunk);
                break;
            case MOVE:
                publishMove(functionChangeHunk);
                break;
            default:
                retainMod(functionChangeHunk);
        }
    }

    private void publishMove(FunctionChangeHunk moveHunk) {
        rememberMove(moveHunk);
        parent.accept(moveHunk);
    }

    private void rememberMove(FunctionChangeHunk move) {
        rememberedMoves.add(move);
    }

    private void retainMod(FunctionChangeHunk functionChangeHunk) {
        retainedMods.put(functionChangeHunk.getFunction(), functionChangeHunk);
    }

    private void retainAddOrDelHunk(Map<String, LinkedList<FunctionChangeHunk>> map, FunctionChangeHunk fh) {
        String functionName = fh.getFunction().functionName;
        LinkedList<FunctionChangeHunk> hunks = map.get(functionName);
        if (hunks == null) {
            hunks = new LinkedList<>();
            map.put(functionName, hunks);
        }
        hunks.add(fh);
    }

    public void mergeAndPublishRemainingHunks() {
        mergeAndPublishAddsAndDels();
        remapAndPublishMods();
    }

    private void mergeAndPublishAddsAndDels() {
        mergeAndPublishExactlyMatchingAddsAndDels();
        mergeAndPublishFuzzyMatchingAddsAndDels();

        for (Map.Entry<String, LinkedList<FunctionChangeHunk>> e : delsByFunctionName.entrySet()) {
            LOG.info("Function was only deleted (but not moved): " + e.getKey());
            publishAll(e.getValue());
        }
        delsByFunctionName.clear();

        for (Map.Entry<String, LinkedList<FunctionChangeHunk>> e : addsByFunctionName.entrySet()) {
            LOG.info("Function was only added (but not moved): " + e.getKey());
            publishAll(e.getValue());
        }
        addsByFunctionName.clear();
    }

    private void mergeAndPublishExactlyMatchingAddsAndDels() {
        Iterator<Map.Entry<String, LinkedList<FunctionChangeHunk>>> addIt = addsByFunctionName.entrySet().iterator();
        while (addIt.hasNext()) {
            Map.Entry<String, LinkedList<FunctionChangeHunk>> addEntry = addIt.next();
            final String functionName = addEntry.getKey();
            final LinkedList<FunctionChangeHunk> adds = addEntry.getValue();
            final LinkedList<FunctionChangeHunk> dels = delsByFunctionName.get(functionName);
            if (dels != null) {
                LOG.info("Merging additions and deletions for same-named function into a move: " + functionName);
                mergeAndPublishHunks(dels, adds);
                addIt.remove();
                delsByFunctionName.remove(functionName);
            }
        }
    }

    private void mergeAndPublishFuzzyMatchingAddsAndDels() {
        class FunctionDistance implements Comparable<FunctionDistance> {
            final Method oldFunction;
            final Method newFunction;
            final float distance;

            public FunctionDistance(Method oldFunction, Method newFunction, float distance) {
                this.oldFunction = oldFunction;
                this.newFunction = newFunction;
                this.distance = distance;
            }

            @Override
            public int compareTo(FunctionDistance o) {
                int r = (int) Math.signum(this.distance - o.distance);
                if (r != 0) return r;
                return Method.COMP_BY_FILE_AND_SIGNATURE.compare(this.oldFunction, this.newFunction);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                FunctionDistance that = (FunctionDistance) o;
                return oldFunction.equals(that.oldFunction) &&
                        newFunction.equals(that.newFunction);
            }

            @Override
            public int hashCode() {
                return Objects.hash(oldFunction, newFunction);
            }
        }

        List<Method> deletedFunctions = extractFunctions(delsByFunctionName);
        List<Method> addedFunctions = extractFunctions(addsByFunctionName);

        SortedSet<FunctionDistance> allDistances = new TreeSet<>();
        for (Method oldFunction : deletedFunctions) {
            for (Method newFunction : addedFunctions) {
                float dist = computeDissimilarity(oldFunction, newFunction);
                if (Float.isFinite(dist)) {
                    FunctionDistance d = new FunctionDistance(oldFunction, newFunction, dist);
                    allDistances.add(d);
                }
            }
        }

        for (FunctionDistance d : allDistances) {
            final Method oldFunction = d.oldFunction;
            final Method newFunction = d.newFunction;
            final String oldFunctionName = oldFunction.functionName;
            final String newFunctionName = newFunction.functionName;

            LinkedList<FunctionChangeHunk> dels = delsByFunctionName.get(oldFunctionName);
            LinkedList<FunctionChangeHunk> adds = addsByFunctionName.get(newFunctionName);

            if ((dels != null) && (adds != null)) {
                LOG.info("Merging deletion and addition into a rename: " + oldFunction + " -> " + newFunction +
                        " edit distance=" + d.distance);

                mergeAndPublishHunks(dels, adds);
                delsByFunctionName.remove(oldFunctionName);
                addsByFunctionName.remove(newFunctionName);
            }
        }

        if (LOG.isDebugEnabled()) {
            Set<String> oldFunctionNamesSeen = new HashSet<>();
            for (FunctionDistance d : allDistances) {
                String oldFunctionName = d.oldFunction.functionName;
                if (!delsByFunctionName.containsKey(oldFunctionName)) continue;
                if (oldFunctionNamesSeen.contains(oldFunctionName)) continue;
                oldFunctionNamesSeen.add(oldFunctionName);
                LOG.debug("Not merging deletion with any add.  Best match would have been: "
                        + d.oldFunction + " -> " + d.newFunction
                        + " edit distance=" + d.distance);
            }
        }
    }

    private static List<Method> extractFunctions(Map<String, LinkedList<FunctionChangeHunk>> delsOrDelsByFunctionName) {
        return delsOrDelsByFunctionName
                .values()
                .stream()
                .filter(l -> !l.isEmpty())
                .map(l -> l.getFirst().getFunction()).collect(Collectors.toList());
    }

//    private static int computeRenameDistance(FunctionChangeHunk del, FunctionChangeHunk add) {
//        Method delFunction = del.getFunction();
//        Method addFunction = add.getFunction();
//
//        return computeRenameDistance(delFunction, addFunction);
//    }
//
//    private static int computeRenameDistance(Method delFunction, Method addFunction) {
//        return computeRenameDistance(delFunction.functionName, addFunction.functionName);
//    }

    private static float computeDissimilarity(Method oldFunction, Method newFunction) {
        String oldDef = oldFunction.getSourceCode();
        String newDef = newFunction.getSourceCode();
        int threshold = (int) Math.round(Math.min(oldDef.length(), newDef.length()) / 4.0);
        threshold = Math.max(threshold, 1);
        final boolean isLogDebug = LOG.isDebugEnabled();
        // Returns distance if initialized without threshold; returns -1 if initialized with threshold in case that the edit distance is too large
        LevenshteinDistance distMeasure = new LevenshteinDistance(threshold);
        int dist = distMeasure.apply(oldDef, newDef);
        if (dist >= 0) {
            LOG.info("Functions could be similar enough for a rename: " + oldFunction.functionName + " -> " + newFunction.functionName +
                    " threshold=" + threshold + " actual distance=" + dist);
            if (isLogDebug) {
                LOG.debug("Functions could be similar enough for a rename: " + oldFunction.functionName + " -> " + newFunction.functionName +
                        " threshold=" + threshold + " actual distance=" + dist + ". Bodies:\n"
                        + oldDef + "\n->\n" + newDef);
            }
            return ((float) dist / threshold);
        } else {
            LOG.info("Functions are too dissimilar for a rename: " + oldFunction.functionName + " -> " + newFunction.functionName +
                    " threshold=" + threshold);
            if (isLogDebug) {
                distMeasure = new LevenshteinDistance();
                dist = distMeasure.apply(oldDef, newDef);
                LOG.debug("Functions are too dissimilar for a rename: " + oldFunction.functionName + " -> " + newFunction.functionName +
                        " threshold=" + threshold + " actual distance=" + dist + ". Bodies:\n"
                        + oldDef + "\n->\n" + newDef);
            }
            return Float.POSITIVE_INFINITY;
        }
    }

    private static float computeRenameDistance(String oldFunctionName, String newFunctionName) {
        int threshold = (int) Math.round(Math.min(oldFunctionName.length(), newFunctionName.length()) / 3.0);
        threshold = Math.max(threshold, 1);
        LevenshteinDistance distMeasure = new LevenshteinDistance();
        int dist = distMeasure.apply(oldFunctionName.toLowerCase(), newFunctionName.toLowerCase());
        if (dist <= threshold) {
            LOG.info("Function names could be similar enough for a rename: " + oldFunctionName + " -> " + newFunctionName +
                    " threshold=" + threshold + " actual distance=" + dist);
            return ((float) dist / threshold);
        } else {
            LOG.info("Function names are too dissimilar for a rename: " + oldFunctionName + " -> " + newFunctionName +
                    " threshold=" + threshold + " actual distance=" + dist);
            return Float.POSITIVE_INFINITY;
        }

//        String delSignature = delFunction.functionSignatureXml;
//        String addSignature = addFunction.functionSignatureXml;
//
//        threshold = (Math.min(delSignature.length(), addSignature.length()) * 4) / 5;
//        threshold = Math.max(threshold, 1);
//        distMeasure = new LevenshteinDistance();
//        // If initialized with a threshold, apply will return -1 if the threshold is exceeded.
//        dist = distMeasure.apply(delSignature, addSignature);
//        if (dist < threshold) {
//            LOG.debug("Function signatures are similar enough for a rename: " + delSignature + " -> " + addSignature);
//            return true;
//        } else {
//            LOG.debug("Function signatures are too dissimilar enough for a rename: " + delSignature + " -> " + addSignature);
//        }
    }

    private void remapAndPublishMods() {
        Set<FunctionChangeHunk> unpublishedMods = new HashSet<>();
        for (List<FunctionChangeHunk> mods : retainedMods.getMap().values()) {
            unpublishedMods.addAll(mods);
        }

        for (FunctionChangeHunk move : rememberedMoves) {
            final int moveHunkNo = move.getHunk().getHunkNo();
            final Method oldFunction = move.getFunction();
            final Method newFunction = move.getNewFunction().get();

            List<FunctionChangeHunk> modsForOldFunction = retainedMods.get(oldFunction);
            if (modsForOldFunction != null) {
                for (FunctionChangeHunk mod : modsForOldFunction) {
                    if (mod.getHunk().getHunkNo() > moveHunkNo) {
                        publishAsModForFunction(mod, newFunction);
                        unpublishedMods.remove(mod);
                    }
                }
            }

            List<FunctionChangeHunk> modsForNewFunction = retainedMods.get(newFunction);
            if (modsForNewFunction != null) {
                for (FunctionChangeHunk mod : modsForNewFunction) {
                    if (mod.getHunk().getHunkNo() < moveHunkNo) {
                        publishAsModForFunction(mod, oldFunction);
                        unpublishedMods.remove(mod);
                    }
                }
            }
        }

        publishAll(unpublishedMods);
    }

    private void publishAsModForFunction(FunctionChangeHunk mod, Method alternativeFunction) {
        FunctionChangeHunk remappedMod = new FunctionChangeHunk(alternativeFunction, mod.getHunk(),
                mod.getModType());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Remapped modification " + mod + " to " + remappedMod);
        }
        parent.accept(remappedMod);
    }

    private interface IAddForDelMatchingStrategy {
        FunctionChangeHunk pickAddForDel(List<FunctionChangeHunk> addsInSameFile, FunctionChangeHunk del);
    }

    private static final IAddForDelMatchingStrategy ADD_FOR_DEL_PERFECT_MATCH = new IAddForDelMatchingStrategy() {
        @Override
        public FunctionChangeHunk pickAddForDel(List<FunctionChangeHunk> addsInSameFile, FunctionChangeHunk del) {
            final int delStart = del.getFunction().start1;
            for (FunctionChangeHunk add : addsInSameFile) {
                final int addStart = add.getASideStartLocation();
                if (delStart == addStart) {
                    return add;
                }
            }

            return null;
        }
    };

    private static final IAddForDelMatchingStrategy ADD_FOR_DEL_FIRST_MATCH = new IAddForDelMatchingStrategy() {
        @Override
        public FunctionChangeHunk pickAddForDel(List<FunctionChangeHunk> addsInSameFile, FunctionChangeHunk del) {
            return addsInSameFile.get(0);
        }
    };

    private void mergeAndPublishHunks(LinkedList<FunctionChangeHunk> dels, LinkedList<FunctionChangeHunk> adds) {
        int count = 0;

        GroupingListMap<String, FunctionChangeHunk> addsByPath = new GroupingListMap<>();
        for (FunctionChangeHunk h : adds) {
            addsByPath.put(h.getFunction().filePath, h);
        }

        publishMovesUsingStrategy(dels, adds, addsByPath, ADD_FOR_DEL_PERFECT_MATCH);
        publishMovesUsingStrategy(dels, adds, addsByPath, ADD_FOR_DEL_FIRST_MATCH);

        while (!dels.isEmpty() && !adds.isEmpty()) {
            if (count > 0) {
                LOG.debug("More than one pair of adds and deletes.");
            }
            FunctionChangeHunk del = dels.pop();
            FunctionChangeHunk add = adds.pop();

            FunctionChangeHunk move = mergeDelAndAddToMove(del, add);
            publishMove(move);

            count++;
        }

        if (!dels.isEmpty()) {
            LOG.debug("Publishing some left-over deletes.");
            publishAll(dels);
        }

        if (!adds.isEmpty()) {
            LOG.debug("Publishing some left-over additions.");
            publishAll(adds);
        }
    }

    private void publishMovesUsingStrategy(LinkedList<FunctionChangeHunk> dels, LinkedList<FunctionChangeHunk> adds, GroupingListMap<String, FunctionChangeHunk> addsByPath, IAddForDelMatchingStrategy matchingStrategy) {
        for (Iterator<FunctionChangeHunk> delIt = dels.iterator(); delIt.hasNext(); ) {
            FunctionChangeHunk del = delIt.next();
            String delPath = del.getFunction().filePath;
            List<FunctionChangeHunk> addsInSameFile = addsByPath.get(delPath);
            if (addsInSameFile == null) continue;

            FunctionChangeHunk add = matchingStrategy.pickAddForDel(addsInSameFile, del);
            if (add == null) continue;

            delIt.remove();
            adds.remove(add);
            addsInSameFile.remove(add);
            if (addsInSameFile.isEmpty()) {
                addsByPath.getMap().remove(delPath);
            }

            FunctionChangeHunk move = mergeDelAndAddToMove(del, add);
            publishMove(move);
        }
    }

    private FunctionChangeHunk mergeDelAndAddToMove(FunctionChangeHunk del, FunctionChangeHunk add) {
        Method delFunc = del.getFunction();
        Method addFunc = add.getFunction();
        ChangeHunk delHunk = del.getHunk();
        ChangeHunk addHunk = add.getHunk();
        int linesDeleted = delHunk.getLinesDeleted();
        int linesAdded = addHunk.getLinesAdded();
        ChangeHunk moveHunk = new ChangeHunk(delHunk.getChangeId(), delHunk.getOldPath(), addHunk.getNewPath(), delHunk.getHunkNo(),
                linesDeleted, linesAdded);
        return new FunctionChangeHunk(delFunc, moveHunk, FunctionChangeHunk.ModificationType.MOVE, addFunc);
    }

    private void publishAll(Collection<FunctionChangeHunk> hs) {
        for (FunctionChangeHunk h : hs) {
            parent.accept(h);
        }
    }
}
