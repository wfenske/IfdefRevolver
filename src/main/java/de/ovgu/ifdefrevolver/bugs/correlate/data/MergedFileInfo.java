package de.ovgu.ifdefrevolver.bugs.correlate.data;

import de.ovgu.ifdefrevolver.bugs.correlate.main.Smell;

import java.util.*;

public class MergedFileInfo implements Comparable<MergedFileInfo> {

    private String filename;
    private Date snapshotDate;
    private int fixCount;
    private int changeCount;
    private long sourceLinesOfCode;

    private Map<Smell, List<Double>> smellScores;

    public MergedFileInfo(String filename, Date snapshotDate) {
        this.filename = filename;
        this.snapshotDate = snapshotDate;
        this.fixCount = 0;
        this.changeCount = 0;
        this.sourceLinesOfCode = 0;
        this.smellScores = new HashMap<>();
    }

    public void addSmells(Smell smell, Collection<Double> scores) {
        if (scores.isEmpty()) {
            throw new IllegalArgumentException(
                    "addSmells(...) does not accept empty lists of smelliness scores!");
        }
        List<Double> existingScores = this.smellScores.get(smell);
        if (existingScores == null) {
            existingScores = new ArrayList<>();
            this.smellScores.put(smell, existingScores);
        }
        existingScores.addAll(scores);
    }

    public void setFixCount(int count) {
        this.fixCount = count;
    }

    public void setChangeCount(int count) {
        this.changeCount = count;
    }

    public void setSourceLinesOfCode(long size) {
        this.sourceLinesOfCode = size;
    }

    public String getFilename() {
        return filename;
    }

    public Date snapshotDate() {
        return snapshotDate;
    }

    public int getSmellCount(Smell smell) {
        List<Double> scores = smellScores.get(smell);
        if (scores == null) {
            return 0;
        } else {
            return scores.size();
        }
    }

    public int getTotalSmellCount() {
        int total = 0;
        for (Smell smell : Smell.values()) {
            total += getSmellCount(smell);
        }
        return total;
    }

    public boolean isSmelly() {
        return !smellScores.isEmpty();
    }

    public boolean hasChanged() {
        return changeCount > 0;
    }

    public boolean wasFixed() {
        return fixCount > 0;
    }

    public int getFixCount() {
        return fixCount;
    }

    public int getChangeCount() {
        return changeCount;
    }

    public long getSourceLinesOfCode() {
        return sourceLinesOfCode;
    }

    @Override
    public String toString() {
        return this.filename + " - Smells: " + this.getTotalSmellCount() + " - Fixed: "
                + this.getFixCount() + " - Changed: " + this.getChangeCount() + " - "
                + this.snapshotDate;
    }

    @Override
    public int compareTo(MergedFileInfo obj) {
        int dateCmp = this.snapshotDate().compareTo(obj.snapshotDate());
        if (dateCmp != 0)
            return dateCmp;
        return this.getFilename().compareTo(obj.getFilename());
    }
}
