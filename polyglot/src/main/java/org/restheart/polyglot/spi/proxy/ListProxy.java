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

import java.util.List;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

/**
 * A {@code List} seen by the guest as a native array, read-only. Elements are wrapped when read.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ListProxy implements ProxyArray {
    private final List<?> list;

    /**
     * @param list the list, null is an empty array
     */
    public ListProxy(List<?> list) {
        this.list = list == null ? List.of() : list;
    }

    /**
     * @return the wrapped list
     */
    public List<?> list() {
        return list;
    }

    @Override
    public Object get(long index) {
        if (index < 0 || index >= list.size()) {
            throw new ArrayIndexOutOfBoundsException((int) index);
        }

        return Native.wrap(list.get((int) index));
    }

    @Override
    public void set(long index, Value value) {
        throw new UnsupportedOperationException("read-only array");
    }

    @Override
    public long getSize() {
        return list.size();
    }

    @Override
    public String toString() {
        return list.toString();
    }
}
