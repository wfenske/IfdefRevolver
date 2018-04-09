package de.ovgu.ifdefrevolver.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A map that holds multiple values for the same key.  Used to group elements by keys.
 *
 * @param <K> Type of keys of this map
 * @param <V> Type of values of this map
 * @param <C> Type of collection in which multiple values for the same key are stored
 */
public abstract class GroupingMap<K, V, C extends Collection<V>> {
    protected final Map<K, C> map;

    public GroupingMap() {
        this.map = newMap();
    }

    protected abstract C newCollection();

    protected Map<K, C> newMap() {
        return new HashMap<>();
    }

    public void put(K key, V value) {
        C valuesForKey = map.get(key);
        if (valuesForKey == null) {
            valuesForKey = newCollection();
            map.put(key, valuesForKey);
        }
        valuesForKey.add(value);
    }

    public C get(K key) {
        return map.get(key);
    }

    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    public Map<K, C> getMap() {
        return this.map;
    }
}
