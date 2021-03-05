/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
package org.restheart.cache.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import com.google.common.collect.Maps;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <K> the class of the keys.
 * @param <V> the class of the values (is Optional-ized).
 */
public class HashMapLoadingCache<K, V> implements org.restheart.cache.LoadingCache<K, V> {
    private final HashMap<K, Optional<V>> wrapped;
    private final Function<K, V> loader ;

    public HashMapLoadingCache(Function<K, V> loader) {
        this.wrapped = Maps.newHashMap();
        this.loader = loader;
    }

    @Override
    public Optional<V> get(K key) {
        return wrapped.get(key);
    }

    @Override
    public Optional<V> getLoading(K key) {
        if(wrapped.containsKey(key)) {
            return get(key);
        } else {
            var value = Optional.ofNullable(loader.apply(key));

            wrapped.put(key, value);

            return value;
        }
    }

    @Override
    public void put(K key, V value) {
        wrapped.put(key, Optional.ofNullable(value));
    }

    @Override
    public void invalidate(K key) {
        wrapped.remove(key);
    }

    @Override
    public void invalidateAll() {
        wrapped.clear();
    }

    @Override
    public Map<K, Optional<V>> asMap() {
        return wrapped;
    }

    @Override
    public void cleanUp() {
        // nothing to do
    }
}
