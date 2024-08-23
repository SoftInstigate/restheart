/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.restheart.utils.ThreadsUtils;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <K> the class of the keys.
 * @param <V> the class of the values (is Optional-ized).
 */
public class CaffeineLoadingCache<K, V> implements org.restheart.cache.LoadingCache<K, V> {
    private static final Executor virtualThreadsExecutor = ThreadsUtils.virtualThreadsExecutor();
    private final AsyncLoadingCache<K, Optional<V>> wrapped;

    public CaffeineLoadingCache(long size, EXPIRE_POLICY expirePolicy, long ttl, Function<K, V> loader) {
        var builder = Caffeine.newBuilder();

        builder.maximumSize(size);

        if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_WRITE) {
            builder.expireAfterWrite(ttl, TimeUnit.MILLISECONDS);
        } else if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_READ) {
            builder.expireAfterAccess(ttl, TimeUnit.MILLISECONDS);
        }

        wrapped = builder
            .executor(virtualThreadsExecutor)
            .buildAsync(new AsyncCacheLoader<K, Optional<V>>() {
                @Override
                public CompletableFuture<? extends Optional<V>> asyncLoad(K key, Executor executor) throws Exception {
                    return CompletableFuture.supplyAsync(() -> Optional.ofNullable(loader.apply(key)), virtualThreadsExecutor);
                }

                @Override
                public CompletableFuture<? extends Map<? extends K, ? extends Optional<V>>> asyncLoadAll(Set<? extends K> keys, Executor executor) throws Exception {
                    var ret = new HashMap<K, Optional<V>>();
                    keys.stream().forEachOrdered(key -> {
                        ret.put(key, Optional.ofNullable(loader.apply(key)));
                    });

                    return CompletableFuture.supplyAsync(() -> ret, virtualThreadsExecutor);
                }
            });
    }

    @Override
    public Optional<V> get(K key) {
        var cf = wrapped.getIfPresent(key);
        if (cf == null) {
            return null;
        }

        try {
            return cf.get();
        } catch (InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public synchronized Optional<V> remove(K key) {
        var ret = get(key);
        wrapped.synchronous().invalidate(key);
        return ret;
    }

    @Override
    public Optional<V> getLoading(K key) {
        var cf = wrapped.get(key);
        try {
            return cf.get();
        } catch (InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void put(K key, V value) {
        wrapped.synchronous().put(key, Optional.ofNullable(value));
    }

    @Override
    public void invalidate(K key) {
        wrapped.synchronous().invalidate(key);
    }

    @Override
    public void invalidateAll() {
        wrapped.synchronous().invalidateAll();
    }

    @Override
    public Map<K, Optional<V>> asMap() {
        return wrapped.synchronous().asMap();
    }

    @Override
    public void cleanUp() {
        wrapped.synchronous().cleanUp();
    }
}
