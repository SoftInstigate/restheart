/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.cache;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.restheart.cache.impl.GuavaCache;
import org.restheart.cache.impl.GuavaLoadingCache;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class CacheFactory {
    public static <K,V> LoadingCache<K,V> createLocalLoadingCache(long size, Cache.EXPIRE_POLICY expirePolicy, long ttl, Function<K,V> loader) {
        return new GuavaLoadingCache(size, expirePolicy, ttl, loader);
    }
    
    public static <K,V> Cache<K,V> createLocalCache(long size, Cache.EXPIRE_POLICY expirePolicy, long ttl) {
        return new GuavaCache(size, expirePolicy, ttl);
    }
    
    public static <K,V> Cache<K,V> createLocalCache(long size, Cache.EXPIRE_POLICY expirePolicy, long ttl, Consumer<Map.Entry<K, Optional<V>>> remover) {
        return new GuavaCache(size, expirePolicy, ttl, remover);
    }
}