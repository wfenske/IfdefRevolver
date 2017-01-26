package de.ovgu.skunk.bugs.eval.output;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import de.ovgu.skunk.bugs.eval.data.MergedFileInfo;
import de.ovgu.skunk.bugs.eval.data.SnapshotCorrelationCsvColumn;
import de.ovgu.skunk.bugs.eval.main.Config;

public class PreprocessOutput {
	private static Logger log = Logger.getLogger(PreprocessOutput.class);

	public static void writeCorrelationForSnapshot(List<MergedFileInfo> outputList, String dateStr,
			Config conf) {
		// File correlatedDir = new File(conf.getResultsDir(), "Correlated");
		File correlatedDir = conf.correlatedResultsDir();
		correlatedDir.mkdirs();
		File csvOut = new File(correlatedDir, dateStr + ".csv");

		if (csvOut.exists()) {
			log.warn("File already exists, overwriting: " + csvOut.getAbsolutePath());
			if (!csvOut.delete()) {
				throw new RuntimeException("Error overwriting file " + csvOut.getAbsolutePath());
			}
		}

		BufferedWriter buff = null;

		try {
			buff = new BufferedWriter(new FileWriter(csvOut));
			// Write header
			String header = StringUtils.join(SnapshotCorrelationCsvColumn.values(), ',');
			buff.write(header);
			buff.newLine();

			for (MergedFileInfo info : outputList) {
				String line = SnapshotCorrelationCsvColumn.toCsv(info);
				buff.write(line);
				buff.newLine();
			}
		} catch (IOException e1) {
			throw new RuntimeException("Error writing file " + csvOut.getAbsolutePath(), e1);
		} finally {
			try {
				if (buff != null)
					buff.close();
			} catch (IOException e) {
				// Don't care
			}
		}

		log.info("Successfully wrote correlation data for snapshot " + dateStr + " to "
				+ csvOut.getAbsolutePath());
	}
}
