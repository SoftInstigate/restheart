/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.restheart.utils;

import java.util.AbstractMap;

/**
 * A simple generic pair class that extends AbstractMap.SimpleEntry.
 * This class represents a key-value pair and provides a convenient way
 * to store two related objects together.
 *
 * @param <K> the type of the key
 * @param <V> the type of the value
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Pair<K, V> extends AbstractMap.SimpleEntry<K,V> {
    
    /**
     * Creates a new Pair with the specified key and value.
     *
     * @param key the key to be stored in this pair
     * @param value the value to be stored in this pair
     */
    public Pair(K key, V value) {
        super(key, value);
    }
}
