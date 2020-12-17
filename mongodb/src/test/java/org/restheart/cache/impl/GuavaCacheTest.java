/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.cache.impl;

import java.util.Optional;
import static org.junit.Assert.*;
import org.junit.Test;
import org.restheart.cache.Cache;

/**
 *
 * @author mturatti
 */
@SuppressWarnings({"unchecked","rawtypes"})
public class GuavaCacheTest {

    /**
     *
     */
    public GuavaCacheTest() {
    }

    /**
     *
     */
    @Test
    public void testGet() {
        Object key = "A";
        GuavaCache instance = new GuavaCache(100, Cache.EXPIRE_POLICY.AFTER_WRITE, 10000);
        instance.put(key, 1);
        Optional<Integer> result = instance.get(key);
        assertTrue(result.isPresent());
        assertEquals(Integer.valueOf(1), result.get());
    }

    /**
     *
     */
    @Test
    public void getNonExistent() {
        GuavaCache instance = new GuavaCache(100, Cache.EXPIRE_POLICY.AFTER_WRITE, 10000);
        Optional<Integer> result = instance.get("A");
        assertNull(result);
    }

    /**
     *
     */
    @Test
    public void testPutNull() {
        String key = "B";
        Object value = null;
        GuavaCache instance = new GuavaCache(100, Cache.EXPIRE_POLICY.AFTER_WRITE, 10000);
        instance.put(key, value);
        Optional<Integer> result = instance.get(key);
        assertFalse(result.isPresent());
    }

}
