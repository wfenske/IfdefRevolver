package de.ovgu.skunk.bugs.createsnapshots.input;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;

public class FileFinder {
    /**
     * Return all the files (no directories) below a starting directory whose name matches a pattern.
     *
     * @param startDir        The directory within which to search
     * @param filenamePattern A regular expression patten that the names of files must match.
     *                        Note, the entire filename must match, not just the extension or so.
     *                        For instance, to find all the .c files, use the pattern
     *                        <code>&quot;.*\\.c&quot;</code>.  Patterns are case-insensitive.
     * @return A list of file objects representing the matching files.
     */
    public static List<File> find(File startDir, String filenamePattern) {
        List<File> files = new ArrayList<>(1024);
        Stack<File> dirs = new Stack<>();
        Pattern p = Pattern.compile(filenamePattern, Pattern.CASE_INSENSITIVE);

        if (!startDir.isDirectory())
            throw new IllegalArgumentException("Not a directory: " + startDir.getAbsolutePath());

        dirs.push(startDir);

        while (!dirs.isEmpty())
            for (File file : dirs.pop().listFiles())
                if (file.isDirectory())
                    dirs.push(file);
                else if (p.matcher(file.getName()).matches())
                    files.add(file);

        return files;
    }
}
