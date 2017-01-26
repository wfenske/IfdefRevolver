package de.ovgu.skunk.bugs.concept.input;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;

public class FileFinder {
    public static List<File> find(String startDirName, String extensionPattern) {
        File startDir = new File(startDirName);
        return find(startDir, extensionPattern);
    }

    public static List<File> find(File startDir, String extensionPattern) {
        List<File> files = new ArrayList<File>(1024);
        Stack<File> dirs = new Stack<File>();
        Pattern p = Pattern.compile(extensionPattern, Pattern.CASE_INSENSITIVE);

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
