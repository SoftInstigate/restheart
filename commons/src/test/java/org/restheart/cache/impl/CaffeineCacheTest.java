/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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

import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.restheart.cache.Cache;

/**
 *
 * @author mturatti
 */
public class CaffeineCacheTest {

    public CaffeineCacheTest() {
    }

    @Test
    public void testGet() {
        String key = "A";
        CaffeineCache<String, Integer> instance = new CaffeineCache<>(100, Cache.EXPIRE_POLICY.AFTER_WRITE, 10000);
        instance.put(key, 1);
        Optional<Integer> result = instance.get(key);
        assertTrue(result.isPresent());
        assertEquals(Integer.valueOf(1), result.get());
    }

    @Test
    public void getNonExistent() {
        CaffeineCache<String, Integer> instance = new CaffeineCache<>(100, Cache.EXPIRE_POLICY.AFTER_WRITE, 10000);
        Optional<Integer> result = instance.get("A");
        assertNull(result);
    }

    @Test
    public void testPutNull() {
        String key = "B";
        Integer value = null;
        CaffeineCache<String, Integer> instance = new CaffeineCache<>(100, Cache.EXPIRE_POLICY.AFTER_WRITE, 10000);
        instance.put(key, value);
        Optional<Integer> result = instance.get(key);
        assertFalse(result.isPresent());
    }

    @Test
    public void testRemoval() {
        String key = "B";
        Integer value = 1;

        String removedKey[] = { "" };
        Integer removedValue[] = { -1 };

        CaffeineCache<String, Integer> instance = new CaffeineCache<>(100, Cache.EXPIRE_POLICY.AFTER_WRITE, 10000,
                entry -> {
                    removedKey[0] = entry.getKey();
                    removedValue[0] = entry.getValue().get();
                });

        new Thread(() -> {
            instance.put(key, value);
            instance.invalidate(key);
        }).start();

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            // ntd
        }

        assertEquals(key, removedKey[0]);
        assertEquals(value, removedValue[0]);
    }
}
