package de.ovgu.ifdefrevolver.bugs.minecommits;

import java.util.Optional;

class ProtoCommit implements Comparable<ProtoCommit> {
    public final String commitHash;
    public final Optional<String> parentHash;
    public final String timestamp;

    public ProtoCommit(String commitHash, String timestamp, Optional<String> parentHash) {
        this.commitHash = commitHash;
        this.parentHash = parentHash;
        this.timestamp = timestamp;
    }

    @Override
    public int compareTo(ProtoCommit o) {
        int r;
        r = this.timestamp.compareTo(o.timestamp);
        if (r != 0) return r;
        r = this.commitHash.compareTo(o.commitHash);
        if (r != 0) return r;
        r = compareByParentHash(o);
        return r;
    }

    private int compareByParentHash(ProtoCommit o) {
        return this.parentHashOrEmptyString().compareTo(o.parentHashOrEmptyString());
    }

    private String parentHashOrEmptyString() {
        return parentHash.isPresent() ? parentHash.get() : "";
    }
}
