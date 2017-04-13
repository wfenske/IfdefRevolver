package de.ovgu.ifdefrevolver.bugs.createsnapshots.data;

/**
 * Enumerates possible smells, such as {@link #AB} (for Annotation Bundle),
 * or {@link #LF} (for Large File).
 *
 * @author wfenske
 */
public enum Smell {
    // @formatter:off
    AB("functions.csv", "AnnotationBundle.csm"),
    AF("files.csv", "AnnotationFile.csm"),
    LF("features.csv", "LargeFeature.csm");
    // @formatter:on
    public final String fileName;
    public final String configFileName;

    private Smell(String fileName, String configFileName) {
        this.fileName = fileName;
        this.configFileName = configFileName;
    }
}
