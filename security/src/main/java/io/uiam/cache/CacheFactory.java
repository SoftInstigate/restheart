/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam.cache;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import io.uiam.cache.impl.GuavaCache;
import io.uiam.cache.impl.GuavaLoadingCache;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CacheFactory {
    /**
     * 
     * @param <K> the type of the cache keys
     * @param <V> the type of the cached values
     * @param size the size of the cache
     * @param expirePolicy specifies how and when each entry should be automatically removed from the cache
     * @param ttl Time To Live in milliseconds
     * @param loader the cache loader used to obtain new values
     * @return the cache
    */
    public static <K,V> LoadingCache<K,V> createLocalLoadingCache(long size, Cache.EXPIRE_POLICY expirePolicy, long ttl, Function<K,V> loader) {
        return new GuavaLoadingCache<>(size, expirePolicy, ttl, loader);
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
        return new GuavaCache<>(size, expirePolicy, ttl);
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
        return new GuavaCache<>(size, expirePolicy, ttl, remover);
    }

    private CacheFactory() {
    }
}