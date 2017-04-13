package de.ovgu.ifdefrevolver.bugs.correlate.main;

import java.io.File;

/**
 * Created by wfenske on 13.02.17.
 */
public interface IHasProjectInfoFile {
    /**
     * @return File pointing at the projectInfo.csv
     */
    File projectInfoFile();
}
