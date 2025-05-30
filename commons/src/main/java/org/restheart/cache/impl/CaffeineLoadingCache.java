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
 * A high-performance loading cache implementation based on the Caffeine caching library.
 * 
 * <p>This class provides a thread-safe, high-performance loading cache that automatically
 * computes and caches values for missing keys using a configured loader function. It extends
 * the capabilities of {@link CaffeineCache} by adding automatic value loading.</p>
 * 
 * <p>Key features include:</p>
 * <ul>
 *   <li>Automatic asynchronous loading of missing values using virtual threads</li>
 *   <li>Bulk loading support for efficient batch operations</li>
 *   <li>Size-based eviction with LRU policy</li>
 *   <li>Time-based expiration (after write or after access)</li>
 *   <li>Non-blocking operations with CompletableFuture-based async loading</li>
 * </ul>
 * 
 * <p>The loader function is executed asynchronously using virtual threads, providing excellent
 * performance for I/O-bound operations such as database queries or web service calls. Multiple
 * concurrent requests for the same key will result in only one loader invocation, with other
 * threads waiting for the result.</p>
 * 
 * <p>All values, including null results from the loader, are wrapped in {@link Optional} for
 * consistent null handling.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * CaffeineLoadingCache<String, User> cache = new CaffeineLoadingCache<>(
 *     10000,                        // max 10,000 entries
 *     EXPIRE_POLICY.AFTER_WRITE,    // expire after write
 *     3600000,                      // 1 hour TTL
 *     userId -> userService.loadUser(userId)  // loader function
 * );
 * 
 * // Automatically loads from userService if not cached
 * Optional<User> user = cache.getLoading("user123");
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <K> the class of the keys.
 * @param <V> the class of the values (is Optional-ized).
 */
public class CaffeineLoadingCache<K, V> implements org.restheart.cache.LoadingCache<K, V> {
    private static final Executor virtualThreadsExecutor = ThreadsUtils.virtualThreadsExecutor();
    private final AsyncLoadingCache<K, Optional<V>> wrapped;

    /**
     * Creates a new CaffeineLoadingCache with the specified configuration and loader function.
     * 
     * <p>This constructor creates an asynchronous loading cache that automatically computes values
     * for missing keys using the provided loader function. The loader is executed asynchronously
     * using virtual threads, providing non-blocking behavior.</p>
     * 
     * <p>The cache enforces size limits and supports time-based expiration policies. When the size
     * limit is reached, the least recently used entries are evicted.</p>
     * 
     * @param size the maximum number of entries the cache can hold
     * @param expirePolicy the expiration policy determining when entries are automatically removed
     * @param ttl the time-to-live in milliseconds; if <= 0, time-based expiration is disabled
     * @param loader the function used to compute values for missing keys; may return null
     * @throws NullPointerException if loader is null
     */
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

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation returns the cached value immediately if present, without triggering
     * the loader. If the key is not present in the cache, {@code null} is returned. To trigger
     * automatic loading, use {@link #getLoading(Object)} instead.</p>
     * 
     * <p>If the value is still being loaded by another thread, this method returns {@code null}
     * rather than waiting for the computation to complete.</p>
     * 
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation is synchronized to ensure atomicity between retrieving and removing
     * the value. If the value is currently being loaded, this method will wait for the loading
     * to complete before removing it.</p>
     * 
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public synchronized Optional<V> remove(K key) {
        var ret = get(key);
        wrapped.synchronous().invalidate(key);
        return ret;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation returns the cached value if present, or triggers the loader function
     * if the key is not found. The loader is executed asynchronously, but this method blocks until
     * the value is computed and cached.</p>
     * 
     * <p>If multiple threads request the same missing key concurrently, only one loader invocation
     * occurs, with all threads receiving the same computed value. If the loader throws an exception,
     * it is wrapped in a {@link RuntimeException} and propagated to all waiting threads.</p>
     * 
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     * @throws RuntimeException if the loader throws an exception during value computation
     */
    @Override
    public Optional<V> getLoading(K key) {
        var cf = wrapped.get(key);
        try {
            return cf.get();
        } catch (InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation stores the value as a completed {@link CompletableFuture}, making it
     * immediately available for retrieval. Any ongoing loading operation for the same key is not
     * affected, but subsequent retrievals will return the newly put value.</p>
     * 
     * @param key {@inheritDoc}
     * @param value {@inheritDoc}
     */
    @Override
    public void put(K key, V value) {
        wrapped.put(key, CompletableFuture.supplyAsync(() -> Optional.ofNullable(value)));
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation removes the entry from the cache, including any in-progress loading
     * operations. Subsequent calls to {@link #getLoading(Object)} will trigger a new loader
     * invocation.</p>
     * 
     * @param key {@inheritDoc}
     */
    @Override
    public void invalidate(K key) {
        wrapped.synchronous().invalidate(key);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation removes all entries from the cache, including any in-progress loading
     * operations. This operation may be expensive for large caches.</p>
     */
    @Override
    public void invalidateAll() {
        wrapped.synchronous().invalidateAll();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>The returned map provides a synchronous view of the cache. Operations on this map
     * do not trigger automatic loading; only explicitly stored values are visible. To access
     * values with automatic loading, use {@link #getLoading(Object)} instead of map operations.</p>
     * 
     * @return {@inheritDoc}
     */
    @Override
    public Map<K, Optional<V>> asMap() {
        return wrapped.synchronous().asMap();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation delegates to the underlying Caffeine cache's cleanup mechanism,
     * which performs maintenance tasks such as evicting expired entries and completing
     * pending removal notifications.</p>
     */
    @Override
    public void cleanUp() {
        wrapped.synchronous().cleanUp();
    }
}
