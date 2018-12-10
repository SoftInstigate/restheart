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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Test;

import io.uiam.cache.Cache;

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
