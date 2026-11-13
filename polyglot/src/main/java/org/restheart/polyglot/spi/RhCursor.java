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
package org.restheart.polyglot.spi;

import java.util.ArrayList;

import org.bson.BsonDocument;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyIterable;
import org.graalvm.polyglot.proxy.ProxyIterator;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.restheart.polyglot.spi.proxy.BsonProxy;

import com.mongodb.client.MongoIterable;

/**
 * The cursor of a {@code find} or an {@code aggregate}: iterable with {@code for...of} and the
 * spread operator, and with {@code toArray()}, {@code first()} and {@code forEach(fn)}.
 *
 * <p>Iteration is blocking and lazy: documents are fetched from the server in batches as the
 * plugin walks them. {@code toArray()} reads them all.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhCursor implements ProxyIterable, ProxyObject {
    private final MongoIterable<BsonDocument> iterable;

    RhCursor(MongoIterable<BsonDocument> iterable) {
        this.iterable = iterable;
    }

    @Override
    public Object getIterator() {
        var it = iterable.iterator();
        return new ProxyIterator() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Object getNext() {
                return BsonProxy.toGuest(it.next());
            }
        };
    }

    /**
     * @return every document, as a native array
     */
    public Object toArray() {
        var docs = new ArrayList<Object>();
        iterable.forEach(d -> docs.add(BsonProxy.toGuest(d)));
        return ProxyArray.fromList(docs);
    }

    /**
     * @return the first document, null when there is none
     */
    public Object first() {
        return BsonProxy.toGuest(iterable.first());
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "toArray" -> (ProxyExecutable) args -> toArray();
            case "first" -> (ProxyExecutable) args -> first();
            case "forEach" -> (ProxyExecutable) args -> {
                if (args.length == 0 || !args[0].canExecute()) {
                    throw new IllegalArgumentException("forEach needs a function");
                }
                iterable.forEach(d -> args[0].executeVoid(BsonProxy.toGuest(d)));
                return null;
            };
            default -> null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return ProxyArray.fromArray("toArray", "first", "forEach");
    }

    @Override
    public boolean hasMember(String key) {
        return switch (key) {
            case "toArray", "first", "forEach" -> true;
            default -> false;
        };
    }

    @Override
    public void putMember(String key, org.graalvm.polyglot.Value value) {
        throw new UnsupportedOperationException("a cursor is read-only");
    }
}
