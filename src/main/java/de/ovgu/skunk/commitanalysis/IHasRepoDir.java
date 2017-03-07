package de.ovgu.skunk.commitanalysis;

/**
 * Created by wfenske on 06.03.17.
 */
public interface IHasRepoDir {
    char OPT_REPO = 'r';
    String OPT_REPO_L = "repo";

    void validateRepoDir();

    String getRepoDir();

    void setRepoDir(String repoDir);
}
