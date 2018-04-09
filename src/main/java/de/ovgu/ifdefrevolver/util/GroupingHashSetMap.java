package de.ovgu.ifdefrevolver.util;

import java.util.HashSet;

public class GroupingHashSetMap<K, V> extends GroupingMap<K, V, HashSet<V>> {
    @Override
    protected HashSet<V> newCollection() {
        return new HashSet<>();
    }
}
