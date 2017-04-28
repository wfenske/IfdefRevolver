package de.ovgu.ifdefrevolver.bugs.minecommits;

import de.ovgu.skunk.detection.output.CsvEnumUtils;
import org.apache.log4j.Logger;
import org.repodriller.domain.Commit;
import org.repodriller.domain.Modification;
import org.repodriller.persistence.PersistenceMechanism;
import org.repodriller.scm.CommitVisitor;
import org.repodriller.scm.SCMRepository;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BugfixCommitVisitor implements CommitVisitor {
    private static Logger LOG = Logger.getLogger(BugfixCommitVisitor.class);

    private final DateFormat dateFormat = new SimpleDateFormat(RevisionsFullColumns.TIMESTAMP_FORMAT);
    private String[] bugFixTerms;

    public BugfixCommitVisitor(String[] bugFixTerms) {
        this.bugFixTerms = bugFixTerms;
    }

    @Override
    public void process(SCMRepository repo, Commit commit, PersistenceMechanism writer) {
        if (commit.isMerge()) {
            LOG.info("Ignoring merge commit " + commit.getHash());
            return;
        }

        Calendar cal = commit.getDate();
        String formattedTimeStamp;
        synchronized (dateFormat) {
            formattedTimeStamp = dateFormat.format(cal.getTime());
        }


        String commitMsg = commit.getMsg().toLowerCase();
        boolean containsABug = false;
        StringBuilder foundWords = new StringBuilder();
        for (int ixBugFixTerm = 0; ixBugFixTerm < bugFixTerms.length; ixBugFixTerm++) {
            // boolean containsABug = commitMsg.contains("fix") ||
            // commitMsg.contains("bug");
            String keyword = bugFixTerms[ixBugFixTerm];
            // Erkennt welches Wort gefunden wird (nur für den Überblick)
            if (commitMsg.contains(keyword)) {
                // Insert separator in case multiple keywords are found
                if (containsABug) {
                    foundWords.append(" ");
                }
                foundWords.append(keyword);
                containsABug = true;
            }
        }

        //boolean inMainBranch = commit.isInMainBranch();
        //final String branches = branchNamesCsvOut(commit.getBranches());

        /* holt alle Modifikationen eines Commits */
        boolean commitMadeItToOutput = false;
        for (Modification m : commit.getModifications()) {

            /* führt den git diff Befehl für das File aus */
            String diff = m.getDiff();

            /*
             * entfernen aller nicht C Dateien, da nur in .c-Dateien Smells
             * auftreten können
             */
            String fileName = m.getFileName();
            // if(!fileName.contains(".c")) continue;
            if (!fileName.matches(".*\\.[cC]$"))
                continue;

            /*
             * Wenn Modification Type nicht MODIFY ist entfernen -> denn Adds
             * und Deletes können im Prinzip kein Bugfix sein
             */
            // if(m.getType() != ModificationType.MODIFY) continue;

            /* Wenn Kein Bugfix Commit entfernen */
            // if(!containsABug) continue;

            /* RegEx für die geänderten Lines */
            Matcher newMatch = Pattern.compile("@@ .* @@").matcher(diff);
            while (newMatch.find()) {
                String strMatch = newMatch.group();
                String[] parts = strMatch.split(" ");
                String[] delParts = parts[1].split(",");
                String[] addParts = parts[2].split(",");
                String delLine = delParts[0].substring(1);
                String addLine = addParts[0].substring(1);

                /* schreibt aktuellen Datensatz in die CSV */
                writer.write(commit.getHash(), containsABug, foundWords.toString(), m.getFileName(), m.getType(),
                        delLine, addLine, formattedTimeStamp
                        //, inMainBranch, branches
                );
                commitMadeItToOutput = true;
            }
        }

        if (!commitMadeItToOutput) {
            LOG.info("Ignoring commit " + commit.getHash() + ". No relevant modifications to .c files were found.");
        }
    }

    private static String branchNamesCsvOut(Set<String> branches) {
        Iterator<String> it = branches.iterator();
        if (!it.hasNext())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (; ; ) {
            String e = it.next();
            if (e.indexOf('"') != -1) {
                e = e.replace("\"", "");
            }
            sb.append(e);
            if (!it.hasNext())
                return sb.append('"').toString();
            sb.append(',');
        }
    }

    @Override
    public String name() {
        return "keyword-based bug-fix identifier";
    }

    public String[] getOutputFileHeader() {
        return CsvEnumUtils.headerRowStrings(RevisionsFullColumns.class);
    }
}
