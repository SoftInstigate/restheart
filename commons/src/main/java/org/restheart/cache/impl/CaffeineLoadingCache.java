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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <K> the class of the keys.
 * @param <V> the class of the values (is Optional-ized).
 */
public class CaffeineLoadingCache<K, V> implements org.restheart.cache.LoadingCache<K, V> {
    private final LoadingCache<K, Optional<V>> wrapped;

    public CaffeineLoadingCache(long size, EXPIRE_POLICY expirePolicy, long ttl, Function<K, V> loader) {
        var builder = Caffeine.newBuilder();

        builder.maximumSize(size);

        if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_WRITE) {
            builder.expireAfterWrite(ttl, TimeUnit.MILLISECONDS);
        } else if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_READ) {
            builder.expireAfterAccess(ttl, TimeUnit.MILLISECONDS);
        }

        wrapped = builder.build(new CacheLoader<K, Optional<V>>() {
            @Override
            public Optional<V> load(K key) throws Exception {
                return Optional.ofNullable(loader.apply(key));
            }

            @Override
            public Map<? extends K, ? extends Optional<V>> loadAll(Set<? extends K> keys) throws Exception {
                var ret = new HashMap<K, Optional<V>>();
                keys.stream().forEachOrdered(key -> ret.put(key, Optional.ofNullable(loader.apply(key))));
                return ret;
            }
        });
    }

    @Override
    public Optional<V> get(K key) {
        return wrapped.getIfPresent(key);
    }

    @Override
    public Optional<V> getLoading(K key) {
        return wrapped.get(key);
    }

    @Override
    public void put(K key, V value) {
        wrapped.put(key, Optional.ofNullable(value));
    }

    @Override
    public void invalidate(K key) {
        wrapped.invalidate(key);
    }

    @Override
    public void invalidateAll() {
        wrapped.invalidateAll();
    }

    @Override
    public Map<K, Optional<V>> asMap() {
        return wrapped.asMap();
    }

    @Override
    public void cleanUp() {
        wrapped.cleanUp();
    }
}
