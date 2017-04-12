package de.ovgu.skunk.util;

import java.io.File;

/**
 * Created by wfenske on 12.04.17.
 */
public class FileUtils {
    /**
     * Prevent instantiation: This is supposed to be a collection of static helper functions
     */
    private FileUtils() {
    }


    /**
     * @param file The file to test
     * @return <code>true</code> if and only if the given file is a readable, regular file with a file size &gt; 0.
     */
    public static boolean isNonEmptyRegularFile(File file) {
        return file.exists() &&
                file.isFile() &&
                file.canRead() &&
                (file.length() > 0);
    }
}
