package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Context;
import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.detection.input.SrcMlFolderReader;

public class GitMethod extends Method {
    /**
     * @param ctx
     * @param signature                 the signature
     * @param gitFilePath               file path as provided by GIT
     * @param start1                    the starting line of the function within it's file (first line in the file is
     *                                  counted as 1)
     * @param grossLoc                  length of the function in lines of code, may include empty lines
     * @param signatureGrossLinesOfCode length of the function signature in lines of code, as it appears in the file
     */
    public GitMethod(Context ctx, String signature, String gitFilePath, int start1, int grossLoc,
                     int signatureGrossLinesOfCode, String fullFunctionCode) {
        super(ctx, signature, gitFilePath, start1, grossLoc, signatureGrossLinesOfCode, fullFunctionCode);
        this.netLoc = SrcMlFolderReader.countSloc(fullFunctionCode);
    }

    @Override
    public String ProjectRelativeFilePath() {
        return filePath;
    }
}
