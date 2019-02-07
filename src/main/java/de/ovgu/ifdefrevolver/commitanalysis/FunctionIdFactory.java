package de.ovgu.ifdefrevolver.commitanalysis;

import java.util.HashMap;
import java.util.Map;

public class FunctionIdFactory {
    private Map<String, String> internedStrings = new HashMap<>();
    private Map<FunctionId, FunctionId> internedFunctionIds = new HashMap<>();

    public FunctionId intern(String signature, String file) {
        signature = internString(signature);
        file = internString(file);
        FunctionId newId = new FunctionId(signature, file);
        FunctionId existingId = internedFunctionIds.get(newId);
        if (existingId != null) {
            return existingId;
        } else {
            internedFunctionIds.put(newId, newId);
            return newId;
        }
    }

    private String internString(String s) {
        if (s == null) return s;
        String existing = internedStrings.get(s);
        if (existing != null) {
            return existing;
        } else {
            internedStrings.put(s, s);
            return s;
        }
    }
}
