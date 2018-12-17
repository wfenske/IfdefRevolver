package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.commitanalysis.ListChangedFunctionsConfig;
import org.apache.commons.cli.CommandLine;

public class AddChangeDistancesConfig extends ListChangedFunctionsConfig {
    /**
     * Size of a commit window, requires positive integer argument
     */
    public static final String OPT_COMMIT_WINDOW_SIZE_L = "windowsize";
    public static final char OPT_COMMIT_WINDOW_SIZE = 'w';

    /**
     * Slide of a commit window, requires positive integer argument
     */
    public static final String OPT_COMMIT_WINDOW_SLIDE_L = "windowslide";
    public static final char OPT_COMMIT_WINDOW_SLIDE = 's';

    public static int DEFAULT_WINDOW_SIZE = 5;
    public static int DEFAULT_WINDOW_SLIDE = 2;


    private int windowSize = DEFAULT_WINDOW_SIZE;
    private int windowSlide = DEFAULT_WINDOW_SLIDE;

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public int getWindowSlide() {
        return windowSlide;
    }

    public void setWindowSlide(int windowSlide) {
        this.windowSlide = windowSlide;
    }

    public static void parseWindowSizeFromCommandLine(CommandLine line, AddChangeDistancesConfig res) {
        if (line.hasOption(OPT_COMMIT_WINDOW_SIZE)) {
            int v = parsePositiveIntOrDie(line, OPT_COMMIT_WINDOW_SIZE, OPT_COMMIT_WINDOW_SIZE_L);
            res.setWindowSize(v);
        }
    }

    public static void parseWindowSlideFromCommandLine(CommandLine line, AddChangeDistancesConfig res) {
        if (line.hasOption(OPT_COMMIT_WINDOW_SLIDE)) {
            int v = parsePositiveIntOrDie(line, OPT_COMMIT_WINDOW_SLIDE, OPT_COMMIT_WINDOW_SLIDE_L);
            res.setWindowSlide(v);
        }
    }

    private static int parsePositiveIntOrDie(CommandLine line, char shortOptName, String longOptName) {
        final String windowSizeString = line.getOptionValue(shortOptName);
        int v;
        try {
            v = Integer.valueOf(windowSizeString);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid value for option `--" + longOptName
                    + "': Not a valid integer: " + windowSizeString);
        }
        if (v < 1) {
            throw new RuntimeException("Invalid value for option `--" + longOptName
                    + "': Value must be an integer >= 1.");
        }
        return v;
    }
}
