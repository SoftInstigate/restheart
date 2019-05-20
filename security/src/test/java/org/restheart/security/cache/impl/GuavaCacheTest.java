/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.cache.impl;

import org.restheart.security.cache.impl.GuavaCache;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Test;

import org.restheart.security.cache.Cache;

/**
 *
 * @author mturatti
 */
public class GuavaCacheTest {

    public GuavaCacheTest() {
    }

    @Test
    public void testGet() {
        String key = "A";
        GuavaCache<String, Integer> instance = new GuavaCache(100, Cache.EXPIRE_POLICY.AFTER_WRITE, 10000);
        instance.put(key, 1);
        Optional<Integer> result = instance.get(key);
        assertTrue(result.isPresent());
        assertEquals(Integer.valueOf(1), result.get());
    }

    @Test
    public void getNonExistent() {
        GuavaCache<String, Integer> instance = new GuavaCache(100, Cache.EXPIRE_POLICY.AFTER_WRITE, 10000);
        Optional<Integer> result = instance.get("A");
        assertNull(result);
    }

    @Test
    public void testPutNull() {
        String key = "B";
        Integer value = null;
        GuavaCache<String, Integer> instance = new GuavaCache(100, Cache.EXPIRE_POLICY.AFTER_WRITE, 10000);
        instance.put(key, value);
        Optional<Integer> result = instance.get(key);
        assertFalse(result.isPresent());
    }
}
