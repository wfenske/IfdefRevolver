package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.log4j.Logger;
import org.eclipse.jgit.diff.*;

public class FunctionSimilarityComputer {
    private static final Logger LOG = Logger.getLogger(FunctionSimilarityComputer.class);

    private final Method fOld;
    private final Method fNew;
    private RawTextComparator rawTextCmp = RawTextComparator.WS_IGNORE_ALL;

    public FunctionSimilarityComputer(Method fOld, Method fNew) {
        this.fOld = fOld;
        this.fNew = fNew;
    }

    /**
     * Compute the ratio of lines that the old function and new function definition have in common. Changes in
     * whitespace are ignored.
     *
     * @return Value between 0 (nothing similar) to 1.0 (all lines similar)
     */
    public static float getRatioOfCommonLines(Method fOld, Method fNew) {
        FunctionSimilarityComputer cmp = new FunctionSimilarityComputer(fOld, fNew);
        return cmp.getRatioOfCommonLines();
    }

    /**
     * Compute the ratio of lines that the old function and new function definition have in common. Changes in
     * whitespace are ignored.
     *
     * @return Value between 0 (nothing similar) to 1.0 (all lines similar)
     */
    public float getRatioOfCommonLines() {
        RawText a = toRawText(fOld);
        RawText b = toRawText(fNew);
        DiffAlgorithm diffAlgorithm = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM);
        EditList editList = diffAlgorithm.diff(rawTextCmp, a, b);
        float commonRatio = commonRatioFromEditList(editList);
        return commonRatio;
    }

    private float commonRatioFromEditList(EditList editList) {
        final int numAllLines = fOld.getGrossLoc() + fNew.getGrossLoc();
        if (numAllLines == 0) {
            return 0.0f;
        }

        int numDeleted = 0;
        int numAdded = 0;

        for (Edit e : editList) {
            numDeleted += e.getLengthA();
            numAdded += e.getLengthB();
        }

        int numSame = numAllLines - numDeleted - numAdded;
        numSame = Math.max(numSame, 0);

        return ((float) numSame) / ((float) (numAllLines));
    }

    private static RawText toRawText(Method f) {
        return new RawText(f.getSourceCode().getBytes());
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

    public static float levenshteinSimilarity(Method fOld, Method fNew) {
        FunctionSimilarityComputer cmp = new FunctionSimilarityComputer(fOld, fNew);
        return cmp.levenshteinSimilarity();
    }

    private float levenshteinSimilarity() {
        String oldDef = fOld.getSourceCode();
        String newDef = fNew.getSourceCode();
        int maxPossibleDist = Math.max(Math.max(oldDef.length(), newDef.length()), 1);
        final boolean isLogDebug = LOG.isDebugEnabled();
        // Returns distance if initialized without threshold; returns -1 if initialized with threshold in case that the edit distance is too large
        LevenshteinDistance distMeasure = new LevenshteinDistance(maxPossibleDist);
        int dist = distMeasure.apply(oldDef, newDef);
        if (dist >= 0) {
//            LOG.info("Functions could be similar enough for a rename: " + fOld.functionName + " -> " + fNew.functionName +
//                    " threshold=" + threshold + " actual distance=" + dist);
//            if (isLogDebug) {
//                LOG.debug("Functions could be similar enough for a rename: " + fOld.functionName + " -> " + fNew.functionName +
//                        " threshold=" + threshold + " actual distance=" + dist + ". Bodies:\n"
//                        + oldDef + "\n->\n" + newDef);
//            }
            final int numNotEdited = maxPossibleDist - dist;
            float similarity = ((float) numNotEdited) / ((float) maxPossibleDist);
            similarity = Math.min(similarity, 1.0f);
            similarity = Math.max(similarity, 0.0f);
            return similarity;
        } else {
//            LOG.info("Functions are too dissimilar for a rename: " + fOld.functionName + " -> " + fNew.functionName +
//                    " threshold=" + threshold);
//            if (isLogDebug) {
//                distMeasure = new LevenshteinDistance();
//                dist = distMeasure.apply(oldDef, newDef);
//                LOG.debug("Functions are too dissimilar for a rename: " + fOld.functionName + " -> " + fNew.functionName +
//                        " threshold=" + threshold + " actual distance=" + dist + ". Bodies:\n"
//                        + oldDef + "\n->\n" + newDef);
//            }
            return 0.0f;
        }
    }
}
