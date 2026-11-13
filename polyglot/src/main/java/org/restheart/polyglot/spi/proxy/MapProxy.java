/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.polyglot.spi.proxy;

import java.util.ArrayList;
import java.util.Map;

import org.graalvm.polyglot.proxy.ProxyArray;

/**
 * A {@code Map<String, Object>} seen by the guest as a native object, read-only.
 *
 * <p>Nested maps and lists are wrapped when they are read, not when the proxy is created, so
 * handing a large configuration or document to a plugin costs nothing until the plugin looks
 * into it.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MapProxy extends ReadOnlyObject {
    private final Map<String, ?> map;

    /**
     * @param map the map, null is an empty object
     */
    public MapProxy(Map<String, ?> map) {
        this.map = map == null ? Map.of() : map;
    }

    /**
     * @return the wrapped map
     */
    public Map<String, ?> map() {
        return map;
    }

    @Override
    public Object getMember(String key) {
        return Native.wrap(map.get(key));
    }

    @Override
    public Object getMemberKeys() {
        return ProxyArray.fromList(new ArrayList<Object>(map.keySet()));
    }

    @Override
    public boolean hasMember(String key) {
        return map.containsKey(key);
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
