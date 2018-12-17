package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.AbResRow;
import de.ovgu.ifdefrevolver.commitanalysis.AllFunctionsRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;

public class JointFunctionAbSmellRow {
    public final FunctionId functionId;
    public final Commit commit;
    public final AllFunctionsRow allFunctionsRow;
    public final AbResRow abResRow;

    public JointFunctionAbSmellRow(Commit commit, AllFunctionsRow allFunctionsRow, AbResRow abResRow) {
        this.commit = commit;
        this.functionId = allFunctionsRow.functionId;
        this.allFunctionsRow = allFunctionsRow;
        this.abResRow = abResRow;
    }

    public JointFunctionAbSmellRow(Commit commit, AllFunctionsRow allFunctionsRow) {
        this(commit, allFunctionsRow, AbResRow.dummyRow(allFunctionsRow.functionId, allFunctionsRow.loc));
    }
}
