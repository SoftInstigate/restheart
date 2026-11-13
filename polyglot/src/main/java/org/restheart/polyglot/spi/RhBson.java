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

import java.util.Map;

import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import org.graalvm.polyglot.Value;
import org.restheart.polyglot.spi.proxy.BsonProxy;
import org.restheart.polyglot.spi.proxy.MapProxy;

/**
 * The {@code bson} helper of the SDK, for the two things extended JSON makes verbose: reading
 * an id, and making a new one.
 *
 * <p>{@code bson.oid(doc._id)} is the hex string of {@code { $oid: "..." }}, or of a string
 * that already is one; {@code bson.newOid()} is a fresh {@code { $oid: "..." }}. There is no
 * wrapper class on purpose: an {@code ObjectId} constructor would have to exist at runtime in
 * every guest language, while {@code { $oid }} is plain data.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhBson {
    /**
     * @param v {@code { $oid: "..." }}, or a 24 characters hex string
     * @return the hex string, null for null and undefined
     */
    public String oid(Value v) {
        if (v == null || v.isNull()) {
            return null;
        }

        if (v.isString()) {
            if (!ObjectId.isValid(v.asString())) {
                throw new IllegalArgumentException("not an ObjectId: " + v.asString());
            }
            return v.asString();
        }

        var bson = GuestValues.toBson(v);

        if (bson.isObjectId()) {
            return bson.asObjectId().getValue().toHexString();
        }

        throw new IllegalArgumentException("not an ObjectId: " + v);
    }

    /**
     * @return a new ObjectId as {@code { $oid: "..." }}
     */
    public Object newOid() {
        return new MapProxy(Map.of("$oid", new ObjectId().toHexString()));
    }

    /**
     * @param v a string or bytes
     * @return whether it is a valid ObjectId hex string
     */
    public boolean isOid(Value v) {
        return v != null && v.isString() && ObjectId.isValid(v.asString());
    }

    /**
     * Parses a JSON string with extended JSON wrappers to a document, as {@code BsonUtils.parse}
     * does for request bodies.
     *
     * @param json the text
     * @return the document as native data
     */
    public Object parse(String json) {
        return BsonProxy.toGuest(BsonDocument.parse(json));
    }

    /**
     * @param v a guest value
     * @return its canonical extended JSON text
     */
    public String stringify(Value v) {
        var b = GuestValues.toBson(v);
        return b.isDocument() ? b.asDocument().toJson() : GuestValues.bsonToJson(b).toString();
    }
}
