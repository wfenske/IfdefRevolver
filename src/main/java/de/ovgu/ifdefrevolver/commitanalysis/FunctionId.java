package de.ovgu.ifdefrevolver.commitanalysis;

import java.util.Comparator;

public class FunctionId {
    public final String signature;
    public final String file;

    public static final Comparator<FunctionId> BY_FILE_AND_SIGNATURE_ID = new Comparator<FunctionId>() {
        @Override
        public int compare(FunctionId o1, FunctionId o2) {
            int r = o1.file.compareTo(o2.file);
            if (r != 0) return r;
            return o1.signature.compareTo(o2.signature);
        }
    };

    public FunctionId(String signature, String file) {
        this.signature = signature;
        this.file = file;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FunctionId)) return false;

        FunctionId that = (FunctionId) o;

        if (!signature.equals(that.signature)) return false;
        return file.equals(that.file);
    }

    @Override
    public int hashCode() {
        int result = signature.hashCode();
        result = 31 * result + file.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "FunctionId{" +
                "signature='" + signature + '\'' +
                ", file='" + file + '\'' +
                '}';
    }
}
