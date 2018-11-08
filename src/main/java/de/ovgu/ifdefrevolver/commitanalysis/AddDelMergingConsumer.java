package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.util.GroupingListMap;
import de.ovgu.skunk.detection.data.Method;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.function.Consumer;

/**
 * Consumes change hunks of different types. In case a function is deleted and subsequently moved, the two edits are
 * aggregated to form a function move change.  Other changes are passed on as is.
 * <p>
 * Created by wfenske on 2018-03-21.
 */
public class AddDelMergingConsumer implements Consumer<FunctionChangeHunk> {
    private static final Logger LOG = Logger.getLogger(AddDelMergingConsumer.class);

    private Set<String> allAddedAndDeletedFunctionNames = new LinkedHashSet<>();
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
                //rememberPotentialRename(functionChangeHunk);
                publishMove(functionChangeHunk);
                break;
            default:
                retainMod(functionChangeHunk);
        }
    }

    protected void publishMove(FunctionChangeHunk moveHunk) {
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
        allAddedAndDeletedFunctionNames.add(functionName);
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
        for (final String functionName : allAddedAndDeletedFunctionNames) {
            final LinkedList<FunctionChangeHunk> adds = addsByFunctionName.get(functionName);
            final LinkedList<FunctionChangeHunk> dels = delsByFunctionName.get(functionName);
            if (adds == null) {
                if (dels == null) {
                    LOG.warn("This should never happen.");
                    continue;
                } else {
                    LOG.debug("Function was only deleted (but not moved): " + functionName);
                    publishAll(dels);
                }
            } else { // We have some adds. Let's see whether we also have dels.
                if (dels == null) {
                    LOG.debug("Function was newly added (but not moved): " + functionName);
                    publishAll(adds);
                } else {
                    LOG.debug("Merging additions and deletions for function into a move: " + functionName);
                    mergeAndPublishHunks(dels, adds);
                }
            }
        }
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

    private void mergeAndPublishHunks(LinkedList<FunctionChangeHunk> dels, LinkedList<FunctionChangeHunk> adds) {
        int count = 0;

        GroupingListMap<String, FunctionChangeHunk> addsByPath = new GroupingListMap<>();
        for (FunctionChangeHunk h : adds) {
            addsByPath.put(h.getFunction().filePath, h);
        }

        for (Iterator<FunctionChangeHunk> delIt = dels.iterator(); delIt.hasNext(); ) {
            FunctionChangeHunk del = delIt.next();
            String delPath = del.getFunction().filePath;
            List<FunctionChangeHunk> addsInSameFile = addsByPath.get(delPath);
            if (addsInSameFile != null) {
                FunctionChangeHunk add = addsInSameFile.get(0);

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

    private FunctionChangeHunk mergeDelAndAddToMove(FunctionChangeHunk del, FunctionChangeHunk add) {
        Method delFunc = del.getFunction();
        Method addFunc = add.getFunction();
        ChangeHunk delHunk = del.getHunk();
        ChangeHunk addHunk = add.getHunk();
        ChangeHunk moveHunk = new ChangeHunk(delHunk.getCommitId(), delHunk.getOldPath(), addHunk.getNewPath(), delHunk.getHunkNo(),
                0, 0);
        return new FunctionChangeHunk(delFunc, moveHunk, FunctionChangeHunk.ModificationType.MOVE, addFunc);
    }

    private void publishAll(Collection<FunctionChangeHunk> hs) {
        for (FunctionChangeHunk h : hs) {
            parent.accept(h);
        }
    }
}
