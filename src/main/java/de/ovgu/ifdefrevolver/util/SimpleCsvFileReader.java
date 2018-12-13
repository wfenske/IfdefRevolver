package de.ovgu.ifdefrevolver.util;

import com.opencsv.CSVReader;
import de.ovgu.ifdefrevolver.bugs.correlate.input.CSVHelper;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by wfenske on 22.03.18.
 */
public abstract class SimpleCsvFileReader<TResult> {
    protected TResult readFile(File file) {
        CSVReader reader = null;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(file);
            reader = new CSVReader(fileReader);
            String[] nextLine;
            initializeResult();
            if (hasHeader()) {
                beforeReadHeader();
                nextLine = reader.readNext();
                if (nextLine != null) {
                    processHeader(nextLine);
                }
                afterReadHeader();
            }
            beforeReadBody();
            while ((nextLine = reader.readNext()) != null) {
                processContentLine(nextLine);
            }
            afterReadBody();
            return finalizeResult();
        } catch (IOException ioe) {
            throw new RuntimeException(
                    "Error reading file " + file.getAbsolutePath(), ioe);
        } finally {
            CSVHelper.silentlyCloseReaders(reader, fileReader);
        }
    }

    protected void initializeResult() {
    }

    protected TResult finalizeResult() {
        return null;
    }

    protected boolean hasHeader() {
        return false;
    }

    protected void processHeader(String[] headerLine) {
    }

    protected abstract void processContentLine(String[] line);

    protected void beforeReadHeader() {
    }

    protected void afterReadHeader() {
    }

    protected void beforeReadBody() {
    }

    protected void afterReadBody() {
    }
}
