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

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <K> the class of the keys
 * @param <V> the class of the values
 */
public interface Cache<K,V> {
    public enum EXPIRE_POLICY { NEVER, AFTER_WRITE, AFTER_READ };
    
    public Optional<V> get(K key);
    
    public void put(K key, V value);
    
    public void invalidate(K key);
    
    public Map<K, Optional<V>> asMap();
}