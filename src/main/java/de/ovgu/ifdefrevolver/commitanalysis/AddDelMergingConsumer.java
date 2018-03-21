package de.ovgu.ifdefrevolver.commitanalysis;

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

    Set<String> allSignatures = new LinkedHashSet<>();
    Map<String, LinkedList<FunctionChangeHunk>> delsByFunctionSignature = new LinkedHashMap<>();
    Map<String, LinkedList<FunctionChangeHunk>> addsByFunctionSignature = new LinkedHashMap<>();
    final Consumer<FunctionChangeHunk> parent;


    public AddDelMergingConsumer(Consumer<FunctionChangeHunk> parent) {
        this.parent = parent;
    }

    @Override
    public void accept(FunctionChangeHunk functionChangeHunk) {
        switch (functionChangeHunk.getModType()) {
            case ADD:
                rememberHunk(addsByFunctionSignature, functionChangeHunk);
                break;
            case DEL:
                rememberHunk(delsByFunctionSignature, functionChangeHunk);
                break;
            default:
                LOG.debug("Passing on non-add, non-del type change.");
                parent.accept(functionChangeHunk);
        }
    }

    private void rememberHunk(Map<String, LinkedList<FunctionChangeHunk>> map, FunctionChangeHunk fh) {
        String signature = fh.getFunction().functionSignatureXml;
        allSignatures.add(signature);
        LinkedList<FunctionChangeHunk> hunks = map.get(signature);
        if (hunks == null) {
            hunks = new LinkedList<>();
            map.put(signature, hunks);
        }
        hunks.add(fh);
    }

    public void mergeAndPublishRemainingHunks() {
        for (final String signature : allSignatures) {
            final LinkedList<FunctionChangeHunk> adds = addsByFunctionSignature.get(signature);
            final LinkedList<FunctionChangeHunk> dels = delsByFunctionSignature.get(signature);
            if (adds == null) {
                if (dels == null) {
                    LOG.warn("This should never happen.");
                    continue;
                } else {
                    LOG.debug("Function was only deleted (but not moved): " + signature);
                    publishAll(dels);
                }
            } else { // We have some adds. Let's see whether we also have dels.
                if (dels == null) {
                    LOG.debug("Function was newly added (but not moved): " + signature);
                    publishAll(adds);
                } else {
                    LOG.debug("Merging additions and deletions for function into a move: " + signature);
                    mergeAndPublishHunks(dels, adds);
                }
            }
        }
    }

    private void mergeAndPublishHunks(LinkedList<FunctionChangeHunk> dels, LinkedList<FunctionChangeHunk> adds) {
        int count = 0;
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
        ChangeHunk delHunk = del.getHunk();
        ChangeHunk addHunk = add.getHunk();
        ChangeHunk moveHunk = new ChangeHunk(delHunk.getCommitId(), delHunk.getOldPath(), addHunk.getNewPath(), delHunk.getHunkNo(),
                delHunk.getLinesDeleted(), addHunk.getLinesAdded());
        return new FunctionChangeHunk(delFunc, moveHunk, FunctionChangeHunk.ModificationType.MOVE);
    }

    private void publishAll(List<FunctionChangeHunk> hs) {
        for (FunctionChangeHunk h : hs) {
            parent.accept(h);
        }
    }
}
