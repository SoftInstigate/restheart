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

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <K> the class of the keys.
 * @param <V> the class of the values (is Optional-ized).
 */
public class CaffeineCache<K, V> implements org.restheart.cache.Cache<K, V> {
    private final Cache<K, Optional<V>> wrapped;

    public CaffeineCache(long size, EXPIRE_POLICY expirePolicy, long ttl) {
        var builder = Caffeine.newBuilder();

        builder.maximumSize(size);

        if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_WRITE) {
            builder.expireAfterWrite(ttl, TimeUnit.MILLISECONDS);
        } else if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_READ) {
            builder.expireAfterAccess(ttl, TimeUnit.MILLISECONDS);
        }

        wrapped = builder.build();
    }

    public CaffeineCache(long size, EXPIRE_POLICY expirePolicy, long ttl, Consumer<Map.Entry<K, Optional<V>>> remover) {
        var builder = Caffeine.newBuilder();

        builder.maximumSize(size);

        if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_WRITE) {
            builder.expireAfterWrite(ttl, TimeUnit.MILLISECONDS);
        } else if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_READ) {
            builder.expireAfterAccess(ttl, TimeUnit.MILLISECONDS);
        }


        wrapped = builder.removalListener(
            new RemovalListener<K,Optional<V>>() {
                @Override
                public void onRemoval(@Nullable K k, @Nullable Optional<V> v, @NonNull RemovalCause cause) {
                    remover.accept(new AbstractMap.SimpleEntry<K,Optional<V>>(k, v));
                }
        }).build();
    }

    @Override
    public Optional<V> get(K key) {
        return wrapped.getIfPresent(key);
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
