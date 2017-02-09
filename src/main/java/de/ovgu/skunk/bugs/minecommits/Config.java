package de.ovgu.skunk.bugs.minecommits;

/**
 * Configuration options
 * 
 * @author wfenske
 */
public class Config {
    public static final String DEFAULT_BUGFIX_TERMS = "bug,fix,patch,error";

    public String[] bugfixTerms;
    public String repoPathName;
    public String outputFileName;
    public boolean forceOverwriteOutput = false;
}
