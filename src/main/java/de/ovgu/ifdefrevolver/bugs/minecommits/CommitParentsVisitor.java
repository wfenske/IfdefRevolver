package de.ovgu.ifdefrevolver.bugs.minecommits;

import de.ovgu.skunk.detection.output.CsvEnumUtils;
import org.apache.log4j.Logger;
import org.repodriller.domain.Commit;
import org.repodriller.persistence.PersistenceMechanism;
import org.repodriller.scm.SCMRepository;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;

public class CommitParentsVisitor implements ICommitVisitorWithOutputFileHeader {
    private static Logger LOG = Logger.getLogger(CommitParentsVisitor.class);

    private final DateFormat dateFormat = new SimpleDateFormat(CommitParentsColumns.TIMESTAMP_FORMAT);

    public CommitParentsVisitor() {
    }

    int commitsSeen = 0;

    @Override
    public void process(SCMRepository repo, Commit commit, PersistenceMechanism writer) {
        synchronized (this) {
            commitsSeen++;
        }
        LOG.info("Listing commit " + commitsSeen);

        Calendar cal = commit.getDate();
        String formattedTimeStamp;
        synchronized (dateFormat) {
            formattedTimeStamp = dateFormat.format(cal.getTime());
        }

        final String commitHash = commit.getHash();
        synchronized (writer) {
            Collection<String> parents = commit.getParents();
            if (parents.isEmpty()) {
                writer.write(commitHash, formattedTimeStamp, "");
            } else {
                for (String parentHash : parents) {
                    writer.write(commitHash, formattedTimeStamp, parentHash);
                }
            }
        }
    }

    @Override
    public String name() {
        return "commit parents visitor";
    }

    @Override
    public String[] getOutputFileHeader() {
        return CsvEnumUtils.headerRowStrings(CommitParentsColumns.class);
    }
}
