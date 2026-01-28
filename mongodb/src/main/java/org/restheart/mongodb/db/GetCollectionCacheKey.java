/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.db;

import java.util.Formatter;
import java.util.Optional;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.restheart.utils.BsonUtils;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public record GetCollectionCacheKey(
    Optional<ClientSession> session,
    MongoCollection<BsonDocument> collection,
    BsonDocument sort,
    BsonDocument filter,
    BsonDocument keys,
    BsonArray hints,
    int from,
    int to,
    long cursorId,
    boolean exhausted) {

    /**
     * @param key
     */
    public static GetCollectionCacheKey clone(GetCollectionCacheKey key) {
        return new GetCollectionCacheKey(
            key.session,
            key.collection,
            key.sort,
            key.filter,
            key.keys,
            key.hints,
            key.from,
            key.to,
            key.cursorId,
            key.exhausted);
    }

    String getCacheStatsGroup() {
        try (Formatter f = new Formatter()) {
            return (filter == null ? "no filter" : filter.toString())
                    + " - "
                    + (sort == null ? "no sort" : sort.toString())
                    + " - "
                    + (hints == null ? "no hints" : BsonUtils.toJson(hints))
                    + " - "
                    + f.format("%10d", from)
                    + " - "
                    + f.format("%10d", to);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "[session=%s, collection=%s, sort=%s, filter=%s, keys=%s, hints=%s, from=%s, to=%s, cursorId=%s, exhausted=%s]",
            session,
            collection.getNamespace(),
            sort,
            filter,
            keys,
            hints,
            from,
            to,
            cursorId,
            exhausted);
    }
}
