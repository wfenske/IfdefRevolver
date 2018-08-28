package de.ovgu.ifdefrevolver.util;

/**
 * Thread with a flag that can be used in the run methods's main loop to check whether termination was requested.
 * <p>
 * Created by wfenske on 19.04.17.
 */
public class TerminableThread extends Thread {
    protected boolean terminationRequested = false;

    public void requestTermination() {
        this.terminationRequested = true;
    }
}
