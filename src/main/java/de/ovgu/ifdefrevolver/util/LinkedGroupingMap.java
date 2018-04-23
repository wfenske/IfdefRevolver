package de.ovgu.ifdefrevolver.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A grouping map that will return its entries the order in which the keys were inserted into the map
 */
public abstract class LinkedGroupingMap<K, V, C extends Collection<V>> extends GroupingMap<K, V, C> {
    @Override
    protected Map newMap() {
        return new LinkedHashMap();
    }
}
