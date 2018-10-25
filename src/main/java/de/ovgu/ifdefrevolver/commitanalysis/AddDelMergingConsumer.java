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

    Set<String> allAddedAndDeletedFunctionNames = new LinkedHashSet<>();
    Map<String, LinkedList<FunctionChangeHunk>> delsByFunctionName = new LinkedHashMap<>();
    Map<String, LinkedList<FunctionChangeHunk>> addsByFunctionName = new LinkedHashMap<>();
    final Consumer<FunctionChangeHunk> parent;
    Map<Method, Method> renamesByOldMethod = new HashMap<>();

    public AddDelMergingConsumer(Consumer<FunctionChangeHunk> parent) {
        this.parent = parent;
    }

    @Override
    public void accept(FunctionChangeHunk functionChangeHunk) {
        switch (functionChangeHunk.getModType()) {
            case ADD:
                rememberHunk(addsByFunctionName, functionChangeHunk);
                break;
            case DEL:
                rememberHunk(delsByFunctionName, functionChangeHunk);
                break;
            case MOVE:
                rememberPotentialRename(functionChangeHunk);
                parent.accept(functionChangeHunk);
                break;
            default:
                LOG.debug("Passing on non-add, non-del type change.");
                FunctionChangeHunk hunkAfterResolvingRenames = resolvePotentialRename(functionChangeHunk);
                parent.accept(hunkAfterResolvingRenames);
        }
    }

    private void rememberPotentialRename(FunctionChangeHunk functionChangeHunk) {
        Method oldMethod = functionChangeHunk.getFunction();
        String oldPath = oldMethod.filePath;

        Method newMethod = functionChangeHunk.getNewFunction().get();
        String newPath = newMethod.filePath;

        if (!oldPath.equals(newPath)) {
            LOG.debug("Ignoring " + oldMethod + " being moved to another file as " + newMethod);
            return;
        }

        if (LOG.isDebugEnabled()) {
            Method existingRenaming = renamesByOldMethod.get(oldMethod);
            if (existingRenaming != null) {
                LOG.debug(oldMethod + " has already been renamed once to " + existingRenaming + ". Replacing with new renaming to " + newMethod);
            } else {
                LOG.debug("Remembering renaming of " + oldMethod + " to " + newMethod);
            }
        }

        renamesByOldMethod.put(oldMethod, newMethod);
    }

    private FunctionChangeHunk resolvePotentialRename(FunctionChangeHunk functionChangeHunk) {
        Method originalMethod = functionChangeHunk.getFunction();
        Method renamedMethod = renamesByOldMethod.get(originalMethod);
        if (renamedMethod == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(originalMethod + " has not been renamed. Passing change as is.");
            }
            return functionChangeHunk;
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug(originalMethod + " has been renamed to " + renamedMethod + ". Modifying change to refer to new function.");
            }
            FunctionChangeHunk modifiedHunk = new FunctionChangeHunk(renamedMethod, functionChangeHunk.getHunk(),
                    functionChangeHunk.getModType());
            return modifiedHunk;
        }
    }

    private void rememberHunk(Map<String, LinkedList<FunctionChangeHunk>> map, FunctionChangeHunk fh) {
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
                parent.accept(move);
            }
        }

        while (!dels.isEmpty() && !adds.isEmpty()) {
            if (count > 0) {
                LOG.debug("More than one pair of adds and deletes.");
            }
            FunctionChangeHunk del = dels.pop();
            FunctionChangeHunk add = adds.pop();

            FunctionChangeHunk move = mergeDelAndAddToMove(del, add);
            parent.accept(move);

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

    private void publishAll(List<FunctionChangeHunk> hs) {
        for (FunctionChangeHunk h : hs) {
            parent.accept(h);
        }
    }
}
