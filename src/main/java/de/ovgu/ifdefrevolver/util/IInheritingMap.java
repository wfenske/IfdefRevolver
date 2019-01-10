package de.ovgu.ifdefrevolver.util;

import java.util.Map;
import java.util.Set;

public interface IInheritingMap<K, V> {
    V put(K key, V value);

    V get(K key);

    V remove(K key);

    boolean containsKey(K key);

    Set<Map.Entry<K, V>> entrySet();

    Set<K> keySet();
}
