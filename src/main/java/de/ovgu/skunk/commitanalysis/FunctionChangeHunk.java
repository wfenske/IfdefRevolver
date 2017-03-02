package de.ovgu.skunk.commitanalysis;

import de.ovgu.skunk.detection.data.Method;

/**
 * Information about a hunk of change within a commit and the function that it changes
 * <p>
 * Created by wfenske on 02.03.17.
 */
public class FunctionChangeHunk {
    ChangeHunk hunk;
    Method function;
}
