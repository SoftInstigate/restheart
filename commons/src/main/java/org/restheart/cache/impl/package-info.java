/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
 * Provides concrete cache implementations for the RESTHeart cache abstraction layer.
 * 
 * <p>This package contains the actual cache implementations that back the interfaces
 * defined in {@link org.restheart.cache}. These implementations provide different
 * performance characteristics and features to suit various use cases.</p>
 * 
 * <h2>Available Implementations</h2>
 * 
 * <h3>Caffeine-Based Implementations</h3>
 * <ul>
 *   <li>{@link org.restheart.cache.impl.CaffeineCache} - High-performance cache with
 *       manual value management, size limits, and expiration policies</li>
 *   <li>{@link org.restheart.cache.impl.CaffeineLoadingCache} - Advanced cache with
 *       automatic asynchronous value loading using virtual threads</li>
 * </ul>
 * 
 * <h3>HashMap-Based Implementation</h3>
 * <ul>
 *   <li>{@link org.restheart.cache.impl.HashMapLoadingCache} - Simple loading cache
 *       backed by HashMap, suitable for testing or small datasets</li>
 * </ul>
 * 
 * <h2>Implementation Characteristics</h2>
 * 
 * <table border="1">
 *   <caption>Cache Implementation Comparison</caption>
 *   <tr>
 *     <th>Implementation</th>
 *     <th>Size Limits</th>
 *     <th>Expiration</th>
 *     <th>Loading</th>
 *     <th>Performance</th>
 *     <th>Use Case</th>
 *   </tr>
 *   <tr>
 *     <td>CaffeineCache</td>
 *     <td>Yes</td>
 *     <td>Yes</td>
 *     <td>No</td>
 *     <td>High</td>
 *     <td>General purpose, high-throughput</td>
 *   </tr>
 *   <tr>
 *     <td>CaffeineLoadingCache</td>
 *     <td>Yes</td>
 *     <td>Yes</td>
 *     <td>Async</td>
 *     <td>High</td>
 *     <td>I/O-bound operations, automatic loading</td>
 *   </tr>
 *   <tr>
 *     <td>HashMapLoadingCache</td>
 *     <td>No</td>
 *     <td>No</td>
 *     <td>Sync</td>
 *     <td>Basic</td>
 *     <td>Testing, small datasets</td>
 *   </tr>
 * </table>
 * 
 * <h2>Implementation Details</h2>
 * 
 * <h3>Caffeine-Based Caches</h3>
 * <p>The Caffeine implementations leverage the high-performance Caffeine library to provide:</p>
 * <ul>
 *   <li>Concurrent read and write operations with minimal contention</li>
 *   <li>Efficient memory usage with weak references where appropriate</li>
 *   <li>Configurable eviction policies (size-based LRU)</li>
 *   <li>Time-based expiration (after write or after access)</li>
 *   <li>Asynchronous operations using virtual threads</li>
 *   <li>Optional removal listeners for resource cleanup</li>
 * </ul>
 * 
 * <h3>HashMap-Based Cache</h3>
 * <p>The HashMap implementation provides:</p>
 * <ul>
 *   <li>Simple, predictable behavior</li>
 *   <li>No automatic eviction or expiration</li>
 *   <li>Synchronous loading operations</li>
 *   <li>Unbounded growth (use with caution)</li>
 * </ul>
 * 
 * <h2>Virtual Threads Support</h2>
 * <p>The Caffeine-based implementations utilize virtual threads for asynchronous operations,
 * providing excellent scalability for I/O-bound workloads. This is particularly beneficial
 * for loading caches where values are fetched from external sources like databases or
 * web services.</p>
 * 
 * <h2>Thread Safety</h2>
 * <p>All implementations in this package are thread-safe, though they achieve this through
 * different mechanisms:</p>
 * <ul>
 *   <li>Caffeine caches use lock-free algorithms and concurrent data structures</li>
 *   <li>HashMap cache uses method-level synchronization</li>
 * </ul>
 * 
 * <h2>Choosing an Implementation</h2>
 * <p>Select the appropriate implementation based on your requirements:</p>
 * <ul>
 *   <li>Use {@link CaffeineCache} for general-purpose caching with size limits and expiration</li>
 *   <li>Use {@link CaffeineLoadingCache} when values need to be computed or fetched automatically</li>
 *   <li>Use {@link HashMapLoadingCache} for testing or when you need simple, unbounded caching</li>
 * </ul>
 * 
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li>Caffeine caches have O(1) average time complexity for basic operations</li>
 *   <li>Loading operations depend on the complexity of the loader function</li>
 *   <li>Eviction and expiration checks are performed incrementally during normal operations</li>
 *   <li>The {@link org.restheart.cache.Cache#cleanUp()} method can force immediate cleanup</li>
 * </ul>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 7.0
 * @see org.restheart.cache.Cache
 * @see org.restheart.cache.LoadingCache
 * @see org.restheart.cache.CacheFactory
 */
package org.restheart.cache.impl;
