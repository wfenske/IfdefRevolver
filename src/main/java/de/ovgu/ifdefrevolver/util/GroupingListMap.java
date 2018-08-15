package de.ovgu.ifdefrevolver.util;

import java.util.ArrayList;
import java.util.List;

public class GroupingListMap<K, V> extends GroupingMap<K, V, List<V>> {

    @Override
    protected List<V> newCollection() {
        return new ArrayList<>();
    }
}