package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.correlate.data.IHasSnapshotDate;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.commitanalysis.*;
import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.detection.output.CsvFileWriterHelper;
import de.ovgu.skunk.detection.output.CsvRowProvider;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;

class CachingFunctionsLister {
    private static Logger LOG = Logger.getLogger(CachingFunctionsLister.class);

    private final IHasRepoAndResultsDir config;

    public CachingFunctionsLister(IHasRepoAndResultsDir config) {
        this.config = config;
    }

    public Set<FunctionId> getFunctionIdsAtCommit(CommitsDistanceDb.Commit commit) {
        if (cacheFileExists(commit)) {
            return getFunctionIdsFromCsv(commit);
        } else {
            Map<String, List<Method>> actualFunctionsByPath = GitUtil.listFunctionsAtCurrentCommit(this.config.getRepoDir(), commit.commitHash);
            writeCacheCsvFile(actualFunctionsByPath, commit);
            return extractFunctionIds(actualFunctionsByPath);
        }
    }

    private void writeCacheCsvFile(Map<String, List<Method>> actualFunctionsByPath, CommitsDistanceDb.Commit commit) {
        final File outputFile = AllFunctionsCsvReader.getFileForCommitHash(config, commit.commitHash);
        ensureOutputFileDirOrDie(outputFile);
        Calendar commitDateAsCalendar = GitUtil.getAuthorDateOfCommit(this.config.getRepoDir(), commit.commitHash);
        final Date commitDate = commitDateAsCalendar.getTime();

        CsvFileWriterHelper writer = new CsvFileWriterHelper() {
            IHasSnapshotDate dateProvider = new IHasSnapshotDate() {
                @Override
                public Date getStartDate() {
                    return commitDate;
                }
            };

            CsvRowProvider<Method, IHasSnapshotDate, AllSnapshotFunctionsColumns> csvRowProvider = AllSnapshotFunctionsColumns.newCsvRowProvider(dateProvider);

            @Override
            protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
                csv.printRecord(csvRowProvider.headerRow());
                try {
                    printRecordsForFunctions(csv);
                } catch (IOException | RuntimeException ex) {
                    LOG.warn("Failed to write output file " + outputFile, ex);
                    boolean deleteFailed = false;
                    try {
                        deleteFailed = outputFile.delete();
                    } catch (RuntimeException re) {
                        LOG.warn("Failed to delete incomplete output file " + outputFile, re);
                    }
                    if (deleteFailed) {
                        LOG.warn("Failed to delete output file " + outputFile);
                    }
                }
            }

            private void printRecordsForFunctions(CSVPrinter csv) throws IOException {
                for (List<Method> functions : actualFunctionsByPath.values()) {
                    for (Method f : functions) {
                        final Object[] row = csvRowProvider.dataRow(f);
                        csv.printRecord(row);
                    }
                }
            }
        };

        writer.write(outputFile);
    }

    private void ensureOutputFileDirOrDie(File outputFile) {
        File dir = outputFile.getParentFile();
        if (dir.isDirectory()) {
            return;
        }

        if (!dir.mkdirs()) {
            throw new RuntimeException("Failed to create directory for output file " + outputFile);
        }
    }

    private boolean cacheFileExists(CommitsDistanceDb.Commit commit) {
        return AllFunctionsCsvReader.fileExists(config, commit.commitHash);
    }

    private Set<FunctionId> extractFunctionIds(Map<String, List<Method>> actualFunctionsByPath) {
        final Set<FunctionId> actualIds = new LinkedHashSet<>();
        for (Map.Entry<String, List<Method>> functionsInPath : actualFunctionsByPath.entrySet()) {
            String path = functionsInPath.getKey();
            for (Method f : functionsInPath.getValue()) {
                FunctionId id = new FunctionId(f.uniqueFunctionSignature, path);
                actualIds.add(id);
            }
        }
        return actualIds;
    }

    private Set<FunctionId> getFunctionIdsFromCsv(CommitsDistanceDb.Commit commit) {
        AllFunctionsCsvReader reader = new AllFunctionsCsvReader(new FunctionIdFactory());
        final List<AllFunctionsRow> allFunctionsRows = reader.readFile(config, commit.commitHash);
        Set<FunctionId> result = new LinkedHashSet<>();
        for (AllFunctionsRow r : allFunctionsRows) {
            result.add(r.functionId);
        }
        return result;
    }
}
