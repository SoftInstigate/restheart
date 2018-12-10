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
package io.uiam.cache.impl;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <K> the class of the keys.
 * @param <V> the class of the values (is Optional-ized).
 */
@SuppressWarnings("unchecked")
public class GuavaCache<K, V> implements io.uiam.cache.Cache<K, V> {
    private final Cache<K, Optional<V>> wrapped;

    public GuavaCache(long size, EXPIRE_POLICY expirePolicy, long ttl) {
        CacheBuilder builder = CacheBuilder.newBuilder();

        builder.maximumSize(size);

        if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_WRITE) {
            builder.expireAfterWrite(ttl, TimeUnit.MILLISECONDS);
        } else if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_READ) {
            builder.expireAfterAccess(ttl, TimeUnit.MILLISECONDS);
        }

        wrapped = builder.build();
    }

    public GuavaCache(long size, EXPIRE_POLICY expirePolicy, long ttl, Consumer<Map.Entry<K, Optional<V>>> remover) {
        CacheBuilder builder = CacheBuilder.newBuilder();

        builder.maximumSize(size);

        if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_WRITE) {
            builder.expireAfterWrite(ttl, TimeUnit.MILLISECONDS);
        } else if (ttl > 0 && expirePolicy == EXPIRE_POLICY.AFTER_READ) {
            builder.expireAfterAccess(ttl, TimeUnit.MILLISECONDS);
        }

        wrapped = builder
                .removalListener((RemovalNotification notification) -> {
                    remover.accept(notification);
                })
                .build();
    }

    @Override
    public Optional<V> get(K key) {
        return wrapped.getIfPresent(key);
    }

    @Override
    public void put(K key, V value) {
        wrapped.put(key, Optional.ofNullable(value));
    }

    @Override
    public void invalidate(K key) {
        wrapped.invalidate(key);
    }

    @Override
    public Map<K, Optional<V>> asMap() {
        return wrapped.asMap();
    }
}
