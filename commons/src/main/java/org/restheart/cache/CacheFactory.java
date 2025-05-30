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
package org.restheart.cache;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.restheart.cache.impl.CaffeineCache;
import org.restheart.cache.impl.CaffeineLoadingCache;
import org.restheart.cache.impl.HashMapLoadingCache;

/**
 * Factory class for creating various types of cache implementations.
 * 
 * <p>The {@code CacheFactory} provides static factory methods to create different cache implementations
 * with various features and configurations. It serves as the main entry point for cache creation
 * in RESTHeart, abstracting the underlying implementation details.</p>
 * 
 * <p>The factory supports creating:</p>
 * <ul>
 *   <li>Local caches with size limits and expiration policies</li>
 *   <li>Loading caches that automatically compute missing values</li>
 *   <li>Simple HashMap-based caches for testing or small-scale use</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create a simple cache with TTL
 * Cache<String, User> userCache = CacheFactory.createLocalCache(
 *     1000,                    // max size
 *     EXPIRE_POLICY.AFTER_WRITE, // expiration policy
 *     3600000                  // TTL in milliseconds (1 hour)
 * );
 * 
 * // Create a loading cache with automatic value computation
 * LoadingCache<String, Config> configCache = CacheFactory.createLocalLoadingCache(
 *     100,
 *     EXPIRE_POLICY.AFTER_READ,
 *     300000,                  // 5 minutes TTL
 *     key -> loadConfigFromDatabase(key)
 * );
 * }</pre>
 * 
 * <p>All cache instances created by this factory are thread-safe and suitable for
 * concurrent access in multi-threaded environments.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CacheFactory {
    /**
     * Creates a local loading cache with automatic value computation capabilities.
     * 
     * <p>This method creates a high-performance cache backed by Caffeine that automatically
     * loads values using the provided loader function when a key is not present. The cache
     * enforces size limits and supports various expiration policies.</p>
     * 
     * <p>The loader function is called asynchronously using virtual threads, providing
     * excellent performance characteristics for I/O-bound operations.</p>
     * 
     * <p>Key features:</p>
     * <ul>
     *   <li>Automatic loading of missing values</li>
     *   <li>Asynchronous value computation using virtual threads</li>
     *   <li>Configurable size limits with LRU eviction</li>
     *   <li>Time-based expiration support</li>
     *   <li>Thread-safe with excellent concurrent performance</li>
     * </ul>
     *
     * @param <K>          the type of the cache keys
     * @param <V>          the type of the cached values
     * @param size         the maximum number of entries the cache may contain
     * @param expirePolicy specifies how and when each entry should be automatically
     *                     removed from the cache (NEVER, AFTER_WRITE, or AFTER_READ)
     * @param ttl          Time To Live in milliseconds; if <= 0, entries won't expire based on time
     * @param loader       the function used to compute values for missing keys; may return null
     * @return a new {@link LoadingCache} instance with the specified configuration
     * @throws NullPointerException if loader is null
     */
    public static <K, V> LoadingCache<K, V> createLocalLoadingCache(long size, Cache.EXPIRE_POLICY expirePolicy, long ttl, Function<K, V> loader) {
        return new CaffeineLoadingCache<>(size, expirePolicy, ttl, loader);
    }

    /**
     * Creates a simple HashMap-based loading cache without size limits or expiration.
     * 
     * <p>This method creates a basic cache implementation backed by a {@link HashMap}.
     * Unlike the Caffeine-based caches, this implementation:</p>
     * <ul>
     *   <li>Has no size limits (entries are never evicted)</li>
     *   <li>Has no expiration policy (entries never expire)</li>
     *   <li>Uses synchronous loading (loader is called in the same thread)</li>
     *   <li>Provides basic thread-safety through synchronization</li>
     * </ul>
     * 
     * <p>This cache type is suitable for:</p>
     * <ul>
     *   <li>Testing and development environments</li>
     *   <li>Small datasets where eviction is not needed</li>
     *   <li>Scenarios where simple, predictable behavior is preferred</li>
     * </ul>
     * 
     * <p><strong>Warning:</strong> This cache can grow without bounds and may cause
     * memory issues if used with large or unbounded datasets.</p>
     * 
     * @param <K>    the type of the cache keys
     * @param <V>    the type of the cached values
     * @param loader the function used to compute values for missing keys; may return null
     * @return a new {@link LoadingCache} instance backed by HashMap
     * @throws NullPointerException if loader is null
     */
    public static <K, V> LoadingCache<K, V> createHashMapLoadingCache(Function<K, V> loader) {
        return new HashMapLoadingCache<>(loader);
    }

    /**
     * Creates a local cache with size limits and expiration policies.
     * 
     * <p>This method creates a high-performance cache backed by Caffeine that provides
     * manual cache management without automatic loading capabilities. Values must be
     * explicitly added using {@link Cache#put(Object, Object)}.</p>
     * 
     * <p>The cache features:</p>
     * <ul>
     *   <li>Configurable maximum size with LRU eviction</li>
     *   <li>Time-based expiration (after write or after access)</li>
     *   <li>Thread-safe operations with excellent concurrent performance</li>
     *   <li>Asynchronous maintenance operations using virtual threads</li>
     * </ul>
     * 
     * <p>Use this method when you need a simple cache without automatic loading,
     * for example, to store computation results or frequently accessed data.</p>
     *
     * @param <K> the type of the cache keys
     * @param <V> the type of the cached values
     * @param size the maximum number of entries the cache may contain
     * @param expirePolicy specifies how and when each entry should be automatically 
     *                     removed from the cache (NEVER, AFTER_WRITE, or AFTER_READ)
     * @param ttl Time To Live in milliseconds; if <= 0, entries won't expire based on time
     * @return a new {@link Cache} instance with the specified configuration
     */
    public static <K,V> Cache<K,V> createLocalCache(long size, Cache.EXPIRE_POLICY expirePolicy, long ttl) {
        return new CaffeineCache<>(size, expirePolicy, ttl);
    }
    /**
     * Creates a local cache with size limits, expiration policies, and a custom removal listener.
     * 
     * <p>This method creates a cache similar to {@link #createLocalCache(long, Cache.EXPIRE_POLICY, long)}
     * but with an additional removal listener that is invoked whenever an entry is removed from
     * the cache, either due to expiration, eviction, or explicit removal.</p>
     * 
     * <p>The removal listener is useful for:</p>
     * <ul>
     *   <li>Cleaning up resources associated with cached values</li>
     *   <li>Logging or monitoring cache evictions</li>
     *   <li>Updating secondary data structures or caches</li>
     *   <li>Triggering side effects when entries are removed</li>
     * </ul>
     * 
     * <p>The removal listener is called asynchronously and should not perform blocking operations
     * or throw exceptions. The entry provided to the listener includes the key and the optional
     * value that was removed.</p>
     * 
     * <p>Example usage:</p>
     * <pre>{@code
     * Cache<String, Connection> connectionCache = CacheFactory.createLocalCache(
     *     100,
     *     EXPIRE_POLICY.AFTER_WRITE,
     *     300000,  // 5 minutes
     *     entry -> {
     *         entry.getValue().ifPresent(connection -> connection.close());
     *         logger.info("Removed connection for key: " + entry.getKey());
     *     }
     * );
     * }</pre>
     *
     * @param <K> the type of the cache keys
     * @param <V> the type of the cached values
     * @param size the maximum number of entries the cache may contain
     * @param expirePolicy specifies how and when each entry should be automatically 
     *                     removed from the cache (NEVER, AFTER_WRITE, or AFTER_READ)
     * @param ttl Time To Live in milliseconds; if <= 0, entries won't expire based on time
     * @param remover the consumer to invoke each time a value is automatically removed 
     *                from the cache; receives entries with keys and optional values
     * @return a new {@link Cache} instance with the specified configuration and removal listener
     * @throws NullPointerException if remover is null
     */
    public static <K,V> Cache<K,V> createLocalCache(long size, Cache.EXPIRE_POLICY expirePolicy, long ttl, Consumer<Map.Entry<K, Optional<V>>> remover) {
        return new CaffeineCache<>(size, expirePolicy, ttl, remover);
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     * 
     * <p>This class provides only static factory methods and should not be instantiated.</p>
     */
    private CacheFactory() {
    }
}
