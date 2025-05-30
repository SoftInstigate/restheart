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

/**
 * A generic cache interface that provides basic caching operations with configurable expiration policies.
 * 
 * <p>This interface defines the contract for cache implementations in RESTHeart, supporting
 * various caching strategies and expiration policies. Values stored in the cache are wrapped
 * in {@link Optional} to handle null values gracefully.</p>
 * 
 * <p>Implementations of this interface should be thread-safe and handle concurrent access appropriately.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * Cache<String, User> userCache = CacheFactory.createLocalCache(1000, EXPIRE_POLICY.AFTER_WRITE, 3600000);
 * userCache.put("user123", new User("John Doe"));
 * Optional<User> user = userCache.get("user123");
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <K> the class of the keys
 * @param <V> the class of the values
 */
public interface Cache<K, V> {
    /**
     * Defines the expiration policy for cache entries.
     * 
     * <p>The expiration policy determines when entries should be automatically removed from the cache:</p>
     * <ul>
     *   <li>{@link #NEVER} - Entries never expire automatically and must be explicitly removed</li>
     *   <li>{@link #AFTER_WRITE} - Entries expire after a fixed duration since their creation or last update</li>
     *   <li>{@link #AFTER_READ} - Entries expire after a fixed duration since their last access (read or write)</li>
     * </ul>
     */
    public enum EXPIRE_POLICY {
        /** Entries never expire automatically */
        NEVER, 
        /** Entries expire after a fixed duration since their creation or last update */
        AFTER_WRITE, 
        /** Entries expire after a fixed duration since their last access */
        AFTER_READ
    }

    /**
     * Retrieves the value associated with the specified key from the cache.
     * 
     * <p>This method returns immediately with the cached value if present. If the key
     * is not found in the cache, {@code null} is returned. The returned value is wrapped
     * in an {@link Optional} to handle null values stored in the cache.</p>
     * 
     * @param key the key whose associated value is to be returned
     * @return an {@link Optional} containing the cached value, or {@code null} if the key is not present
     * @throws NullPointerException if the specified key is null (implementation dependent)
     */
    public Optional<V> get(K key);

    /**
     * Removes the mapping for the specified key from the cache and returns the previously associated value.
     * 
     * <p>If the cache previously contained a mapping for the key, the old value is returned
     * wrapped in an {@link Optional}. If no mapping existed, {@code null} is returned.</p>
     * 
     * @param key the key whose mapping is to be removed from the cache
     * @return an {@link Optional} containing the previous value associated with the key,
     *         or {@code null} if there was no mapping
     * @throws NullPointerException if the specified key is null (implementation dependent)
     */
    public Optional<V> remove(K key);

    /**
     * Associates the specified value with the specified key in the cache.
     * 
     * <p>If the cache previously contained a mapping for the key, the old value is replaced.
     * Null values are supported and will be wrapped in an {@link Optional} internally.</p>
     * 
     * <p>Depending on the cache implementation and configuration, this operation may trigger
     * eviction of other entries if the cache size limit is reached.</p>
     * 
     * @param key the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key (may be null)
     * @throws NullPointerException if the specified key is null (implementation dependent)
     */
    public void put(K key, V value);

    /**
     * Performs any pending maintenance operations needed by the cache.
     * 
     * <p>This method triggers cleanup activities such as evicting expired entries,
     * running pending removal listeners, or compacting internal data structures.
     * The exact operations performed are implementation-specific.</p>
     * 
     * <p>Calling this method is typically not necessary as caches usually perform
     * maintenance automatically, but it can be useful in testing scenarios or when
     * you want to ensure cleanup happens at a specific time.</p>
     */
    public void cleanUp();

    /**
     * Discards the cached value for the specified key.
     * 
     * <p>This method removes the entry from the cache without returning the previous value.
     * It is more efficient than {@link #remove(Object)} when the previous value is not needed.</p>
     * 
     * <p>If removal listeners are configured, they will be notified about the invalidation.</p>
     * 
     * @param key the key whose mapping is to be invalidated
     * @throws NullPointerException if the specified key is null (implementation dependent)
     */
    public void invalidate(K key);

    /**
     * Discards all entries in the cache.
     * 
     * <p>This method removes all cached entries, effectively resetting the cache to an empty state.
     * If removal listeners are configured, they will be notified for each invalidated entry.</p>
     * 
     * <p>This operation may be expensive for large caches as it processes all entries.</p>
     */
    public void invalidateAll();

    /**
     * Returns a view of the entries stored in this cache as a thread-safe map.
     * 
     * <p>The returned map is a live view of the cache, meaning that changes to the cache
     * are reflected in the map and vice versa. All optional values in the map are wrapped
     * in {@link Optional} to handle null values consistently.</p>
     * 
     * <p>Operations on the returned map may trigger cache loading, eviction, and removal
     * listener notifications as configured for the cache.</p>
     * 
     * <p>Note that bulk operations on the returned map, such as {@link Map#putAll},
     * are not guaranteed to be performed atomically.</p>
     * 
     * @return a view of this cache as a {@link Map} from keys to {@link Optional} values
     */
    public Map<K, Optional<V>> asMap();
}
