/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
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
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CacheFactory {
    /**
     *
     * @param <K>          the type of the cache keys
     * @param <V>          the type of the cached values
     * @param size         the size of the cache
     * @param expirePolicy specifies how and when each entry should be automatically
     *                     removed from the cache
     * @param ttl          Time To Live in milliseconds
     * @param loader       the cache loader used to obtain new values
     * @return the cache
     */
    public static <K, V> LoadingCache<K, V> createLocalLoadingCache(long size, Cache.EXPIRE_POLICY expirePolicy,
            long ttl, Function<K, V> loader) {
        return new CaffeineLoadingCache<>(size, expirePolicy, ttl, loader);
    }

    /**
     * @param <K>    the type of the cache keys
     * @param <V>    the type of the cached values
     * @param loader the cache loader used to obtain new values
     * @return the cache
     */
    public static <K, V> LoadingCache<K, V> createHashMapLoadingCache(Function<K, V> loader) {
        return new HashMapLoadingCache<>(loader);
    }

    /**
     *
     * @param <K> the type of the cache keys
     * @param <V> the type of the cached values
     * @param size the size of the cache
     * @param expirePolicy specifies how and when each entry should be automatically removed from the cache
     * @param ttl Time To Live in milliseconds
     * @return the cache.
    */
    public static <K,V> Cache<K,V> createLocalCache(long size, Cache.EXPIRE_POLICY expirePolicy, long ttl) {
        return new CaffeineCache<>(size, expirePolicy, ttl);
    }
    /**
     *
     * @param <K> the type of the cache keys.
     * @param <V> the type of the cached values.
     * @param size the size of the cache.
     * @param expirePolicy specifies how and when each entry should be automatically removed from the cache.
     * @param ttl Time To Live in milliseconds.
     * @param remover the cache remover to invoke each time a value is automatically removed from the cache according to the expire xpolicy
     * @return the cache.
    */
    public static <K,V> Cache<K,V> createLocalCache(long size, Cache.EXPIRE_POLICY expirePolicy, long ttl, Consumer<Map.Entry<K, Optional<V>>> remover) {
        return new CaffeineCache<>(size, expirePolicy, ttl, remover);
    }

    private CacheFactory() {
    }
}
