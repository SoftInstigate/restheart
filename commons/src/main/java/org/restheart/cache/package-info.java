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
/**
 * Provides a flexible cache abstraction layer for RESTHeart applications.
 * 
 * <p>This package contains the core caching interfaces and factory classes that enable
 * efficient data caching with various backends and configurations. The abstraction layer
 * allows for easy switching between different cache implementations without changing
 * application code.</p>
 * 
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link org.restheart.cache.Cache} - The base cache interface supporting manual cache management</li>
 *   <li>{@link org.restheart.cache.LoadingCache} - Extended interface with automatic value loading</li>
 *   <li>{@link org.restheart.cache.CacheFactory} - Factory for creating cache instances</li>
 * </ul>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li>Configurable size limits with LRU eviction</li>
 *   <li>Time-based expiration policies (after write or after read)</li>
 *   <li>Automatic value loading for cache misses</li>
 *   <li>Optional removal listeners for cleanup operations</li>
 *   <li>Thread-safe operations with high concurrency support</li>
 *   <li>Null value handling through {@link java.util.Optional}</li>
 * </ul>
 * 
 * <h2>Cache Implementations</h2>
 * <p>The package supports multiple cache backends:</p>
 * <ul>
 *   <li><strong>Caffeine-based caches</strong> - High-performance caches with advanced features
 *       like size limits, expiration, and asynchronous loading</li>
 *   <li><strong>HashMap-based caches</strong> - Simple caches suitable for testing or
 *       small datasets without eviction needs</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <h3>Creating a Simple Cache</h3>
 * <pre>{@code
 * // Create a cache with 1000 entries max, expiring 1 hour after write
 * Cache<String, User> userCache = CacheFactory.createLocalCache(
 *     1000,                        // max size
 *     Cache.EXPIRE_POLICY.AFTER_WRITE,  // expiration policy
 *     3600000                      // TTL in milliseconds
 * );
 * 
 * // Store and retrieve values
 * userCache.put("user123", new User("John Doe"));
 * Optional<User> user = userCache.get("user123");
 * }</pre>
 * 
 * <h3>Creating a Loading Cache</h3>
 * <pre>{@code
 * // Create a cache that automatically loads missing values
 * LoadingCache<String, Config> configCache = CacheFactory.createLocalLoadingCache(
 *     100,                         // max size
 *     Cache.EXPIRE_POLICY.AFTER_READ,   // refresh on access
 *     300000,                      // 5 minutes TTL
 *     key -> loadConfigFromDatabase(key)  // loader function
 * );
 * 
 * // Automatically loads if not present
 * Optional<Config> config = configCache.getLoading("app.settings");
 * }</pre>
 * 
 * <h3>Cache with Removal Listener</h3>
 * <pre>{@code
 * // Create a cache with cleanup on removal
 * Cache<String, Connection> connectionCache = CacheFactory.createLocalCache(
 *     50,
 *     Cache.EXPIRE_POLICY.AFTER_WRITE,
 *     600000,  // 10 minutes
 *     entry -> {
 *         // Clean up resources when entries are removed
 *         entry.getValue().ifPresent(conn -> conn.close());
 *     }
 * );
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * <p>All cache implementations in this package are thread-safe and designed for
 * concurrent access. The Caffeine-based implementations use virtual threads for
 * asynchronous operations, providing excellent performance in high-concurrency
 * scenarios.</p>
 * 
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Choose appropriate cache sizes based on available memory</li>
 *   <li>Select expiration policies that match your data freshness requirements</li>
 *   <li>Use loading caches for expensive computations or I/O operations</li>
 *   <li>Implement removal listeners for resource cleanup when needed</li>
 *   <li>Call {@link Cache#cleanUp()} periodically in low-activity periods</li>
 *   <li>Monitor cache hit rates and adjust configuration as needed</li>
 * </ul>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 7.0
 */
package org.restheart.cache;
