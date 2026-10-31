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
import java.util.Deque;
import java.util.Map;

import org.graalvm.polyglot.proxy.ProxyArray;

/**
 * The query parameters seen by the guest as a native object, read-only.
 *
 * <p>Two views of the same {@code Map<String, Deque<String>>}: {@code req.query.limit} is the
 * first value of {@code limit}, a string, {@code undefined} when absent; {@code req.queryAll.tag}
 * is every value of {@code tag}, an array, for the parameters that repeat. Values are strings,
 * the plugin parses them: a query string carries no types.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class QueryProxy extends ReadOnlyObject {
    private final Map<String, Deque<String>> params;
    private final boolean all;

    /**
     * @param params the query parameters
     * @param all whether a member is every value (an array) or the first value
     */
    public QueryProxy(Map<String, Deque<String>> params, boolean all) {
        this.params = params == null ? Map.of() : params;
        this.all = all;
    }

    @Override
    public Object getMember(String key) {
        var values = params.get(key);

        if (values == null) {
            return null;
        }

        if (all) {
            return ProxyArray.fromList(new ArrayList<Object>(values));
        }

        return values.peekFirst();
    }

    @Override
    public Object getMemberKeys() {
        return ProxyArray.fromList(new ArrayList<Object>(params.keySet()));
    }

    @Override
    public boolean hasMember(String key) {
        return params.containsKey(key);
    }
}
