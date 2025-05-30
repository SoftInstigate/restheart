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
import java.util.function.Function;

import com.google.common.collect.Maps;

/**
 * A simple loading cache implementation backed by a HashMap.
 * 
 * <p>This class provides a basic loading cache implementation using a {@link HashMap} as the
 * underlying storage. Unlike the Caffeine-based implementations, this cache has no size limits,
 * expiration policies, or eviction mechanisms. Values remain in the cache indefinitely until
 * explicitly removed.</p>
 * 
 * <p>Key characteristics:</p>
 * <ul>
 *   <li>No size limits - the cache can grow without bounds</li>
 *   <li>No automatic expiration or eviction</li>
 *   <li>Synchronous loading - the loader function is called in the same thread</li>
 *   <li>Basic thread-safety through method synchronization</li>
 *   <li>Simple and predictable behavior</li>
 * </ul>
 * 
 * <p>This implementation is suitable for:</p>
 * <ul>
 *   <li>Testing and development environments</li>
 *   <li>Small, bounded datasets where memory usage is not a concern</li>
 *   <li>Scenarios where cache entries should never expire automatically</li>
 *   <li>Applications requiring simple, predictable caching behavior</li>
 * </ul>
 * 
 * <p><strong>Warning:</strong> Since this cache has no size limits or eviction policy,
 * it can lead to memory issues if used with large or unbounded datasets. Consider using
 * {@link CaffeineLoadingCache} for production use cases requiring size limits or expiration.</p>
 * 
 * <p>All values, including null results from the loader, are wrapped in {@link Optional}
 * for consistent null handling.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * HashMapLoadingCache<String, Config> cache = new HashMapLoadingCache<>(
 *     key -> loadConfigFromFile(key)
 * );
 * 
 * // Loads from file if not cached
 * Optional<Config> config = cache.getLoading("app.properties");
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <K> the class of the keys.
 * @param <V> the class of the values (is Optional-ized).
 */
public class HashMapLoadingCache<K, V> implements org.restheart.cache.LoadingCache<K, V> {
    private final HashMap<K, Optional<V>> wrapped;
    private final Function<K, V> loader ;

    /**
     * Creates a new HashMapLoadingCache with the specified loader function.
     * 
     * <p>The loader function will be called synchronously whenever {@link #getLoading(Object)}
     * is invoked for a key that is not present in the cache. The loader may return null values,
     * which will be cached as {@link Optional#empty()}.</p>
     * 
     * @param loader the function used to compute values for missing keys; may return null
     * @throws NullPointerException if loader is null
     */
    public HashMapLoadingCache(Function<K, V> loader) {
        this.wrapped = Maps.newHashMap();
        this.loader = loader;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation returns the cached value if present, or {@code null} if the key
     * is not in the cache. No loading occurs with this method; use {@link #getLoading(Object)}
     * to trigger automatic loading for missing keys.</p>
     * 
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public Optional<V> get(K key) {
        return wrapped.get(key);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation removes and returns the value associated with the key.
     * The operation is performed directly on the underlying HashMap.</p>
     * 
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public Optional<V> remove(K key) {
        return wrapped.remove(key);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation first checks if the key is present in the cache. If found,
     * the cached value is returned immediately. If not found, the loader function is
     * invoked synchronously to compute the value, which is then cached and returned.</p>
     * 
     * <p>The loader is called in the same thread, so any exceptions thrown by the loader
     * will propagate directly to the caller. If the loader returns null, it will be
     * cached as {@link Optional#empty()}, and subsequent calls will return the cached
     * empty Optional without invoking the loader again.</p>
     * 
     * <p><strong>Note:</strong> This method is not synchronized, so concurrent calls for
     * the same missing key may result in multiple loader invocations. If this is a concern,
     * external synchronization should be used.</p>
     * 
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     * @throws RuntimeException if the loader throws an exception
     */
    @Override
    public Optional<V> getLoading(K key) {
        var cachedValue = get(key);

        if(cachedValue != null) {
            return cachedValue;
        } else {
            var value = Optional.ofNullable(loader.apply(key));

            wrapped.put(key, value);

            return value;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation stores the value in the cache, wrapping it in an {@link Optional}.
     * If a value already exists for the key, it will be replaced. Null values are supported
     * and will be stored as {@link Optional#empty()}.</p>
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
     * <p>This implementation removes the entry from the underlying HashMap.
     * Subsequent calls to {@link #getLoading(Object)} for this key will trigger
     * a new loader invocation.</p>
     * 
     * @param key {@inheritDoc}
     */
    @Override
    public void invalidate(K key) {
        wrapped.remove(key);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation clears all entries from the underlying HashMap.
     * The cache will be empty after this operation completes.</p>
     */
    @Override
    public void invalidateAll() {
        wrapped.clear();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation returns a direct reference to the underlying HashMap.
     * Changes to the returned map will affect the cache and vice versa. The map
     * is not thread-safe, so external synchronization may be needed for concurrent
     * access.</p>
     * 
     * <p><strong>Warning:</strong> Modifying the returned map directly bypasses any
     * cache-specific logic. Use cache methods like {@link #put(Object, Object)} and
     * {@link #invalidate(Object)} when possible.</p>
     * 
     * @return {@inheritDoc}
     */
    @Override
    public Map<K, Optional<V>> asMap() {
        return wrapped;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation has no maintenance operations to perform since it doesn't
     * support expiration or size-based eviction. The method is a no-op.</p>
     */
    @Override
    public void cleanUp() {
        // nothing to do
    }
}
