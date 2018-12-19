package de.ovgu.ifdefrevolver.bugs.minecommits;

import com.opencsv.CSVReader;
import de.ovgu.ifdefrevolver.bugs.correlate.input.CSVHelper;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Created by wfenske on 2018-02-08
 */
public class CommitsDistanceDbCsvReader {
    private static final Logger LOG = Logger.getLogger(CommitsDistanceDbCsvReader.class);

    public CommitsDistanceDb dbFromCsv(String fileName) {
        File csvFile = new File(fileName);
        return dbFromCsv(csvFile);
    }

    public CommitsDistanceDb dbFromCsv(IHasResultsDir config) {
        File commitParentsFile = new File(config.projectResultsDir(), "commitParents.csv");
        LOG.debug("Reading information about commit parent-child relationships from " + commitParentsFile);
        return dbFromCsv(commitParentsFile);
    }

    protected CommitsDistanceDb dbFromCsv(File csvFile) {
        CSVReader reader = null;
        FileReader fileReader = null;

        List<ProtoCommit> protoCommits = new ArrayList<>();

        try {
            fileReader = new FileReader(csvFile);
            reader = new CSVReader(fileReader);
            String[] header = reader.readNext();

            int ixCommitCol = -1;
            int ixParentCol = -1;
            int ixTimestampCol = -1;

            for (int pos = 0; pos < header.length; pos++) {
                if ((ixCommitCol < 0) && header[pos].equalsIgnoreCase(CommitParentsColumns.COMMIT.name())) {
                    ixCommitCol = pos;
                }
                if ((ixParentCol < 0) && header[pos].equalsIgnoreCase(CommitParentsColumns.PARENT.name())) {
                    ixParentCol = pos;
                }
                if ((ixTimestampCol < 0) && header[pos].equalsIgnoreCase(CommitParentsColumns.TIMESTAMP.name())) {
                    ixTimestampCol = pos;
                }
            }

            if (ixCommitCol < 0) {
                LOG.error("Column " + CommitParentsColumns.COMMIT.name() + " not found.");
            }
            if (ixParentCol < 0) {
                LOG.error("Column " + CommitParentsColumns.PARENT.name() + " not found.");
            }
            if (ixTimestampCol < 0) {
                LOG.error("Column " + CommitParentsColumns.TIMESTAMP.name() + " not found.");
            }
            if ((ixCommitCol < 0) || (ixParentCol < 0) || (ixTimestampCol < 0)) {
                throw new RuntimeException("CVS file has the wrong format. Some columns could not be found (see previous log messages for details). CSV header was: " + Arrays.toString(header));
            }

            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                String timestamp = nextLine[ixTimestampCol];
                String commitHash = nextLine[ixCommitCol];
                String parentHash = nextLine[ixParentCol];
                final Optional<String> optParentHash;
                if ((parentHash == null) || parentHash.isEmpty()) optParentHash = Optional.empty();
                else optParentHash = Optional.of(parentHash);
                ProtoCommit c = new ProtoCommit(commitHash, timestamp, optParentHash);
                protoCommits.add(c);
            }
        } catch (IOException e1) {
            throw new RuntimeException(
                    "Error reading file " + csvFile.getAbsolutePath(), e1);
        } finally {
            CSVHelper.silentlyCloseReaders(reader, fileReader);
        }

        CommitsDistanceDb db = CommitsDistanceDb.fromProtoCommits(protoCommits);
        return db;
    }

    private static List<String[]> sortPairs(List<String[]> pairs) {
        List<String[]> result = new ArrayList<>(pairs);
        Collections.sort(result, new Comparator<String[]>() {
            @Override
            public int compare(String[] o1, String[] o2) {
                int r;
                r = o1[0].compareTo(o2[0]);
                if (r != 0) return r;
                r = o1[1].compareTo(o2[1]);
                if (r != 0) return r;
                return o1[2].compareTo(o2[2]);
            }
        });
        return result;
    }
}
