package de.ovgu.ifdefrevolver.bugs.minecommits;

import com.opencsv.CSVReader;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

/**
 * Created by wfenske on 2018-02-08
 */
public class CommitsDistanceDbCsvReader {
    private static final Logger LOG = Logger.getLogger(CommitsDistanceDbCsvReader.class);

    public CommitsDistanceDb dbFromCsv(String fileName) {
        File csvFile = new File(fileName);
        return dbFromCsv(csvFile);
    }

    public CommitsDistanceDb dbFromCsv(File csvFile) {
        CommitsDistanceDb db = new CommitsDistanceDb();
        CSVReader reader = null;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(csvFile);
            reader = new CSVReader(fileReader);
            String[] header = reader.readNext();

            int ixCommitCol = -1;
            int ixParentCol = -1;

            for (int pos = 0; pos < header.length; pos++) {
                if ((ixCommitCol < 0) && header[pos].equalsIgnoreCase(CommitParentsColumns.COMMIT.name())) {
                    ixCommitCol = pos;
                }
                if ((ixParentCol < 0) && header[pos].equalsIgnoreCase(CommitParentsColumns.PARENT.name())) {
                    ixParentCol = pos;
                }
            }

            if (ixCommitCol < 0) {
                LOG.error("Column " + CommitParentsColumns.COMMIT.name() + " not found.");
            }
            if (ixParentCol < 0) {
                LOG.error("Column " + CommitParentsColumns.PARENT.name() + " not found.");
            }
            if ((ixCommitCol < 0) || (ixParentCol < 0)) {
                throw new RuntimeException("CVS file has the wrong format. Some columns could not be found (see previous log messages for details). CSV header was: " + Arrays.toString(header));
            }

            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                String commitHash = nextLine[ixCommitCol];
                String parentHash = nextLine[ixParentCol];
                if ((parentHash == null) || parentHash.isEmpty()) {
                    db.put(commitHash);
                } else {
                    db.put(commitHash, parentHash);
                }
            }
        } catch (IOException e1) {
            throw new RuntimeException(
                    "Error reading file " + csvFile.getAbsolutePath(), e1);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // We don't care if closing the reader fails.
                }
            } else if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    // We don't care if closing the reader fails.
                }
            }
        }

        return db;
    }
}
