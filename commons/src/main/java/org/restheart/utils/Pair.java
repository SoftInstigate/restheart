package org.restheart.utils;

import java.util.AbstractMap;

public class Pair<K, V> extends AbstractMap.SimpleEntry<K,V> {
    public Pair(K key, V value) {
        super(key, value);
    }
}
