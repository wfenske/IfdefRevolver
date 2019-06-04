package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.util.GroupingListMap;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final float MIN_SIMILARITY = 0.6f;

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

    private static class FunctionSimilarity implements Comparable<FunctionSimilarity> {
        final Method oldFunction;
        final Method newFunction;
        final float similarity;

        public static Comparator<FunctionSimilarity> BY_FUNCTION_FILE_AND_SIGNATURE = new Comparator<FunctionSimilarity>() {
            @Override
            public int compare(FunctionSimilarity o1, FunctionSimilarity o2) {
                int r = Method.COMP_BY_FILE_AND_SIGNATURE.compare(o1.oldFunction, o2.oldFunction);
                if (r != 0) return r;
                return Method.COMP_BY_FILE_AND_SIGNATURE.compare(o1.newFunction, o2.newFunction);
            }
        };

        public FunctionSimilarity(Method oldFunction, Method newFunction, float similarity) {
            this.oldFunction = oldFunction;
            this.newFunction = newFunction;
            this.similarity = similarity;
        }

        @Override
        public int compareTo(FunctionSimilarity o) {
            int r = (int) Math.signum(this.similarity - o.similarity);
            if (r != 0) return r;
            return Method.COMP_BY_FILE_AND_SIGNATURE.compare(this.oldFunction, this.newFunction);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FunctionSimilarity that = (FunctionSimilarity) o;
            return oldFunction.equals(that.oldFunction) &&
                    newFunction.equals(that.newFunction);
        }

        @Override
        public int hashCode() {
            return Objects.hash(oldFunction, newFunction);
        }
    }

    private void mergeAndPublishFuzzyMatchingAddsAndDels() {
        List<Method> deletedFunctions = extractFunctions(delsByFunctionName);
        List<Method> addedFunctions = extractFunctions(addsByFunctionName);

        logSimilarities(deletedFunctions, addedFunctions);

//        SortedSet<FunctionSimilarity> allSimilarities = new TreeSet<>(Comparator.reverseOrder());
//
//        for (Method oldFunction : deletedFunctions) {
//            for (Method newFunction : addedFunctions) {
//                float similarity = FunctionSimilarityComputer.levenshteinSimilarity(oldFunction, newFunction);
//                if (similarity >= MIN_SIMILARITY) {
//                    FunctionSimilarity d = new FunctionSimilarity(oldFunction, newFunction, similarity);
//                    allSimilarities.add(d);
//                }
//            }
//        }

        final int numExpectedComparisons = deletedFunctions.size() * addedFunctions.size();
        final Function<Collection<Method>, Stream<Method>> toStream = getSimilarityStreamFunction(numExpectedComparisons);

        List<FunctionSimilarity> allSimilarities =
                toStream.apply(deletedFunctions)
                        .flatMap(oldFunction ->
                                toStream.apply(addedFunctions)
                                        .map(computeLevenshteinSimilarity(oldFunction))
                                        .filter(sim -> sim.similarity >= MIN_SIMILARITY))
                        .sorted(Comparator.reverseOrder())
                        .collect(Collectors.toList());

        for (FunctionSimilarity d : allSimilarities) {
            final Method oldFunction = d.oldFunction;
            final Method newFunction = d.newFunction;
            final String oldFunctionName = oldFunction.functionName;
            final String newFunctionName = newFunction.functionName;

            LinkedList<FunctionChangeHunk> dels = delsByFunctionName.get(oldFunctionName);
            LinkedList<FunctionChangeHunk> adds = addsByFunctionName.get(newFunctionName);

            if ((dels != null) && (adds != null)) {
                LOG.info("Merging deletion and addition into a rename: " + oldFunction + " -> " + newFunction +
                        " similarity=" + d.similarity);

                mergeAndPublishHunks(dels, adds);
                delsByFunctionName.remove(oldFunctionName);
                addsByFunctionName.remove(newFunctionName);
            }
        }
    }

    private void logSimilarities(List<Method> deletedFunctions, List<Method> addedFunctions) {
        if (!LOG.isDebugEnabled() || deletedFunctions.isEmpty() || addedFunctions.isEmpty()) {
            return;
        }

        int similarityAgree = 0;

        final int numExpectedComparisons = deletedFunctions.size() * addedFunctions.size();
        final Function<Collection<Method>, Stream<Method>> toStream = getSimilarityStreamFunction(numExpectedComparisons);

        List<FunctionSimilarity> levenshteinSimilarities =
                toStream.apply(deletedFunctions)
                        .map(oldFunction ->
                                toStream.apply(addedFunctions)
                                        .map(computeLevenshteinSimilarity(oldFunction))
                                        .max(Comparator.naturalOrder()).get())
                        .sorted(FunctionSimilarity.BY_FUNCTION_FILE_AND_SIGNATURE)
                        .collect(Collectors.toList());

        List<FunctionSimilarity> commonLinesSimilarities =
                toStream.apply(deletedFunctions)
                        .map(oldFunction ->
                                toStream.apply(addedFunctions)
                                        .map(computeCommonLinesSimilarity(oldFunction))
                                        .max(Comparator.naturalOrder()).get())
                        .sorted(FunctionSimilarity.BY_FUNCTION_FILE_AND_SIGNATURE)
                        .collect(Collectors.toList());

        final int numDeletedFunctions = levenshteinSimilarities.size();
        for (int i = 0; i < numDeletedFunctions; i++) {
            FunctionSimilarity highestLevenshteinSimilarity = levenshteinSimilarities.get(i);
            FunctionSimilarity highestDiffSimilarity = commonLinesSimilarities.get(i);
            Method oldFunction = highestLevenshteinSimilarity.oldFunction;
            Method winnerLevenshtein = highestLevenshteinSimilarity.newFunction;
            Method winnerDiff = highestDiffSimilarity.newFunction;

            String oldName = oldFunction.functionName;

            int agree = 0;
            float THRESH = 0.4f;
            if (winnerLevenshtein == winnerDiff) {
                Method winner = winnerLevenshtein;
                LOG.debug("Most similar function to " + oldName + " is " + winner.functionName +
                        " Levenshtein similarity=" + highestLevenshteinSimilarity.similarity +
                        " Diff similarity=" + highestDiffSimilarity.similarity +
                        " Definitions:\n" + sideByBySide(oldFunction.getSourceCode(), winner.getSourceCode()));
                agree = 1;
            } else {
                if ((highestLevenshteinSimilarity.similarity < THRESH) && (highestDiffSimilarity.similarity < THRESH)) {
                    agree = 1;
                }
                StringBuilder msg = new StringBuilder();

                if (highestLevenshteinSimilarity.similarity >= THRESH) {
                    msg.append("Maybe similar: Most Levenshtein-similar function to " + oldName + " is " + winnerLevenshtein.functionName +
                            " Similarity=" + highestLevenshteinSimilarity.similarity +
                            " Definitions:\n" +
                            sideByBySide(oldFunction.getSourceCode(), winnerLevenshtein.getSourceCode()));
                }

                if (highestDiffSimilarity.similarity >= THRESH) {
                    if (msg.length() != 0) msg.append('\n');
                    msg.append("Maybe similar: Most Diff-similar function to " + oldName + " is " + winnerDiff.functionName +
                            " Similarity=" + highestDiffSimilarity.similarity +
                            " Definitions:\n" +
                            sideByBySide(oldFunction.getSourceCode(), winnerDiff.getSourceCode()));
                }

                LOG.debug(msg);
            }

            similarityAgree += agree;
        }

        LOG.debug("Similarity agree: " + similarityAgree + "," + numDeletedFunctions + "," + Math.round(similarityAgree * 100f / numDeletedFunctions));
    }

    private static <E> Function<Collection<E>, Stream<E>> getSimilarityStreamFunction(int numExpectedComparisons) {
        if (numExpectedComparisons > 1000) {
            return Collection::parallelStream;
        } else {
            return Collection::stream;
        }
    }

    private static Function<Method, FunctionSimilarity> computeCommonLinesSimilarity(Method oldFunction) {
        return newFunction -> new FunctionSimilarity(oldFunction, newFunction, FunctionSimilarityComputer.getRatioOfCommonLines(oldFunction, newFunction));
    }

    private static Function<Method, FunctionSimilarity> computeLevenshteinSimilarity(Method oldFunction) {
        return newFunction -> new FunctionSimilarity(oldFunction, newFunction, FunctionSimilarityComputer.levenshteinSimilarity(oldFunction, newFunction));
    }

    private static String shortenLongString(String s) {
        final int MAX_LEN = 80;
        int len = s.length();
        if (len <= MAX_LEN) {
            return s;
        } else {
            return s.substring(0, MAX_LEN - 4) + " ...";
        }
    }

    private static String sideByBySide(String a, String b) {
        String aLines[] = toLines(a);
        String bLines[] = toLines(b);


        int maxLineLen = 0;
        for (int i = 0; i < aLines.length; i++) {
            String l = shortenLongString(aLines[i]);
            aLines[i] = l;
            int len = l.length();
            maxLineLen = Math.max(len, maxLineLen);
        }

        final int maxLines = Math.max(aLines.length, bLines.length);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            String aLine = (i < aLines.length) ? aLines[i] : "";
            String bLine = (i < bLines.length) ? shortenLongString(bLines[i]) : "";
            String sideBySide = String.format("%-" + maxLineLen + "s | %s\n", aLine, bLine);
            out.append(sideBySide);
        }
        return out.toString();
    }

    private static String[] toLines(String txt) {
        return txt.replace("\t", "        ").split("\\n");
    }

    private static List<Method> extractFunctions(Map<String, LinkedList<FunctionChangeHunk>> delsOrDelsByFunctionName) {
        return delsOrDelsByFunctionName
                .values()
                .stream()
                .filter(l -> !l.isEmpty())
                .map(l -> l.getFirst().getFunction()).collect(Collectors.toList());
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
