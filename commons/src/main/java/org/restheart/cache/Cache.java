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

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <K> the class of the keys
 * @param <V> the class of the values
 */
public interface Cache<K,V> {
    public enum EXPIRE_POLICY { NEVER, AFTER_WRITE, AFTER_READ };

    public Optional<V> get(K key);

    public Optional<V> remove(K key);

    public void put(K key, V value);

    /**
     * Performs any pending maintenance operations needed by the cache.
     */
    public void cleanUp();

    public void invalidate(K key);

    public void invalidateAll();

    public Map<K, Optional<V>> asMap();
}
