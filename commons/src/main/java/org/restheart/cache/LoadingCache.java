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

import java.util.Optional;

/**
 * A cache that can automatically load values when they are not present.
 * 
 * <p>A {@code LoadingCache} extends the basic {@link Cache} interface by providing the ability
 * to automatically compute and load values for keys that are not already cached. This is achieved
 * through a loader function that is invoked when {@link #getLoading(Object)} is called for a
 * missing key.</p>
 * 
 * <p>This interface is particularly useful for scenarios where:</p>
 * <ul>
 *   <li>Cache misses are expensive and should trigger automatic value computation</li>
 *   <li>Values can be derived or fetched from an external source (e.g., database, web service)</li>
 *   <li>You want to ensure that a value is always returned (unless the loader returns null)</li>
 * </ul>
 * 
 * <p>The loading mechanism is typically configured when creating the cache instance through
 * {@link CacheFactory}. The loader function defines how to compute values for missing keys.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * LoadingCache<String, User> userCache = CacheFactory.createLocalLoadingCache(
 *     1000, 
 *     EXPIRE_POLICY.AFTER_WRITE, 
 *     3600000,
 *     userId -> userRepository.findById(userId)
 * );
 * 
 * // This will either return the cached user or load it from the repository
 * Optional<User> user = userCache.getLoading("user123");
 * }</pre>
 * 
 * <p>Implementations must be thread-safe and should handle concurrent loading requests for the
 * same key efficiently (typically by ensuring the loader is called only once per key).</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <K> the class of the keys
 * @param <V> the class of the values
 */
public interface LoadingCache<K,V> extends Cache<K,V> {
    /**
     * Returns the value associated with the specified key, loading it if necessary.
     * 
     * <p>If the key is already present in the cache, the cached value is returned immediately.
     * If the key is not present, the cache's loader function is invoked to compute the value,
     * which is then cached and returned.</p>
     * 
     * <p>The loader function is called synchronously, and concurrent requests for the same key
     * will typically result in only one invocation of the loader, with other threads waiting
     * for the computation to complete.</p>
     * 
     * <p>If the loader function returns {@code null}, it will be wrapped in an {@link Optional}
     * and cached. Subsequent calls with the same key will return the cached {@code Optional.empty()}
     * without invoking the loader again (until the entry expires or is evicted).</p>
     * 
     * @param key the key whose associated value is to be returned
     * @return an {@link Optional} containing the value computed by the loader, or 
     *         {@link Optional#empty()} if the loader returned null
     * @throws RuntimeException if the loader throws an exception during value computation
     * @throws NullPointerException if the specified key is null (implementation dependent)
     */
    public Optional<V> getLoading(K key);
}
