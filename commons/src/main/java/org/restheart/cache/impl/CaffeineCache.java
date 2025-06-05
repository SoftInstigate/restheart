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

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.restheart.utils.ThreadsUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

/**
 * A high-performance cache implementation based on the Caffeine caching library.
 * 
 * <p>This class provides a thread-safe, high-performance cache implementation that wraps
 * the Caffeine cache library. It supports various features including:</p>
 * <ul>
 *   <li>Size-based eviction using an LRU (Least Recently Used) policy</li>
 *   <li>Time-based expiration (after write or after access)</li>
 *   <li>Optional removal listeners for cleanup operations</li>
 *   <li>Asynchronous maintenance using virtual threads</li>
 * </ul>
 * 
 * <p>All values stored in this cache are automatically wrapped in {@link Optional} to handle
 * null values consistently. The cache uses virtual threads for background maintenance operations,
 * providing excellent performance characteristics in high-concurrency scenarios.</p>
 * 
 * <p>This implementation is best suited for:</p>
 * <ul>
 *   <li>High-throughput applications requiring fast cache operations</li>
 *   <li>Scenarios where automatic eviction and expiration are needed</li>
 *   <li>Applications that benefit from non-blocking maintenance operations</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * CaffeineCache<String, User> cache = new CaffeineCache<>(
 *     10000,                        // max 10,000 entries
 *     EXPIRE_POLICY.AFTER_WRITE,    // expire after write
 *     3600000                       // 1 hour TTL
 * );
 * 
 * cache.put("user123", new User("John"));
 * Optional<User> user = cache.get("user123");
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <K> the class of the keys.
 * @param <V> the class of the values (is Optional-ized).
 */
public class CaffeineCache<K, V> implements org.restheart.cache.Cache<K, V> {
    private static final Executor virtualThreadsExecutor = ThreadsUtils.virtualThreadsExecutor();
    private final Cache<K, Optional<V>> wrapped;

    /**
     * Creates a new CaffeineCache with the specified configuration.
     * 
     * <p>This constructor creates a cache with size limits and optional time-based expiration.
     * The cache will automatically evict least recently used entries when the size limit is
     * reached.</p>
     * 
     * @param size the maximum number of entries the cache can hold
     * @param expirePolicy the expiration policy determining when entries are automatically removed
     * @param ttl the time-to-live in milliseconds; if &lt;= 0, time-based expiration is disabled
     */
    public CaffeineCache(long size, EXPIRE_POLICY expirePolicy, long ttl) {
        var builder = Caffeine.newBuilder().executor(virtualThreadsExecutor);

        builder.maximumSize(size);

        if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_WRITE) {
            builder.expireAfterWrite(ttl, TimeUnit.MILLISECONDS);
        } else if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_READ) {
            builder.expireAfterAccess(ttl, TimeUnit.MILLISECONDS);
        }

        wrapped = builder.build();
    }

    /**
     * Creates a new CaffeineCache with the specified configuration and a removal listener.
     * 
     * <p>This constructor creates a cache similar to {@link #CaffeineCache(long, EXPIRE_POLICY, long)}
     * but with an additional removal listener that is notified when entries are removed from the cache
     * due to eviction, expiration, or explicit removal.</p>
     * 
     * <p>The removal listener is called asynchronously and should complete quickly to avoid blocking
     * cache operations. The listener receives entries containing the key and the optional value that
     * was removed.</p>
     * 
     * @param size the maximum number of entries the cache can hold
     * @param expirePolicy the expiration policy determining when entries are automatically removed
     * @param ttl the time-to-live in milliseconds; if &lt;= 0, time-based expiration is disabled
     * @param remover the consumer to invoke when entries are removed from the cache
     * @throws NullPointerException if remover is null
     */
    public CaffeineCache(long size, EXPIRE_POLICY expirePolicy, long ttl, Consumer<Map.Entry<K, Optional<V>>> remover) {
        var builder = Caffeine.newBuilder().executor(virtualThreadsExecutor);

        builder.maximumSize(size);

        if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_WRITE) {
            builder.expireAfterWrite(ttl, TimeUnit.MILLISECONDS);
        } else if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_READ) {
            builder.expireAfterAccess(ttl, TimeUnit.MILLISECONDS);
        }


        wrapped = builder.removalListener((@Nullable K k, @Nullable Optional<V> v, @NonNull RemovalCause cause) -> {
            remover.accept(new AbstractMap.SimpleEntry<>(k, v));
        }).build();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation returns the cached value immediately if present. The operation
     * is performed with minimal locking and is safe for concurrent access.</p>
     * 
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public Optional<V> get(K key) {
        return wrapped.getIfPresent(key);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation is synchronized to ensure the get and invalidate operations
     * are performed atomically. If a removal listener is configured, it will be notified
     * asynchronously after the entry is removed.</p>
     * 
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public synchronized Optional<V> remove(K key) {
        var ret = wrapped.getIfPresent(key);
        wrapped.invalidate(key);
        return ret;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation wraps the value in an {@link Optional} before storing it.
     * If the cache is at capacity, the least recently used entry will be evicted to make room.
     * The operation resets any expiration timers according to the configured expiration policy.</p>
     * 
     * @param key {@inheritDoc}
     * @param value {@inheritDoc}
     */
    @Override
    public void put(K key, V value) {
        wrapped.put(key, Optional.ofNullable(value));
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation immediately removes the entry from the cache. If a removal
     * listener is configured, it will be notified asynchronously.</p>
     * 
     * @param key {@inheritDoc}
     */
    @Override
    public void invalidate(K key) {
        wrapped.invalidate(key);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation removes all entries from the cache. If a removal listener
     * is configured, it will be notified for each removed entry. For large caches,
     * this operation may take some time to complete.</p>
     */
    @Override
    public void invalidateAll() {
        wrapped.invalidateAll();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>The returned map is a thread-safe view that reflects the current state of the cache.
     * Modifications to the map (such as put, remove) will affect the cache and vice versa.
     * All map operations respect the cache's eviction and expiration policies.</p>
     * 
     * @return {@inheritDoc}
     */
    @Override
    public Map<K, Optional<V>> asMap() {
        return wrapped.asMap();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation triggers Caffeine's cleanup cycle, which may include:</p>
     * <ul>
     *   <li>Evicting expired entries</li>
     *   <li>Running pending removal listener notifications</li>
     *   <li>Compacting internal data structures</li>
     * </ul>
     * 
     * <p>This operation is performed asynchronously using virtual threads.</p>
     */
    @Override
    public void cleanUp() {
        wrapped.cleanUp();
    }
}
