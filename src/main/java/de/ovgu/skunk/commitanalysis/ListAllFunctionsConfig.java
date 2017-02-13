package de.ovgu.skunk.commitanalysis;

import de.ovgu.skunk.bugs.correlate.main.ProjectInformationConfig;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Created by wfenske on 08.02.17.
 */
public class ListAllFunctionsConfig extends ProjectInformationConfig {
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    @Deprecated
    List<String> filenames;
}
