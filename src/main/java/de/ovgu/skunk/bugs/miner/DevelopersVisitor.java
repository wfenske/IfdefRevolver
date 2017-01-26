package de.ovgu.skunk.bugs.miner;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.repodriller.domain.Commit;
import org.repodriller.domain.Modification;
import org.repodriller.persistence.PersistenceMechanism;
import org.repodriller.scm.CommitVisitor;
import org.repodriller.scm.SCMRepository;

public class DevelopersVisitor implements CommitVisitor {

    private String[] bugFixTerms;
    private int fixCount = 0;

    public DevelopersVisitor(String[] bugFixTerms) {
        this.bugFixTerms = bugFixTerms;
    }

    @Override
    public void process(SCMRepository repo, Commit commit, PersistenceMechanism writer) {
        boolean counted = false;
        Calendar cal = commit.getDate();
        SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd");
        String dateForm = format1.format(cal.getTime());

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

        /* holt alle Modifikationen eines Commits */
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

            // nur hochzählen wenn der Bugfix noch nicht gezählt wurde
            if (!counted && containsABug) {
                counted = true;
                fixCount++;
            }

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
                        delLine, addLine, dateForm, this.fixCount);
            }

            // writer.write(
            // commit.getHash(),
            // containsABug,
            // m.getFileName(),
            // m.getType()
            // );
        }
    }

    @Override
    public String name() {
        return "developers";
    }

}
