/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import java.util.Formatter;
import java.util.Optional;

import org.bson.BsonDocument;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public record CursorPoolEntryKey(
    Optional<ClientSession> session,
    MongoCollection<BsonDocument> collection,
    BsonDocument sort,
    BsonDocument filter,
    BsonDocument keys,
    BsonDocument hint,
    int skipped,
    long cursorId) {

    /**
     * @param key
     */
    public static CursorPoolEntryKey clone(CursorPoolEntryKey key) {
        return new CursorPoolEntryKey(
            key.session,
            key.collection,
            key.filter,
            key.keys,
            key.hint,
            key.sort,
            key.skipped,
            key.cursorId);
    }

    String getCacheStatsGroup() {
        try (Formatter f = new Formatter()) {
            return (filter == null ? "no filter" : filter.toString())
                    + " - "
                    + (sort == null ? "no sort" : sort.toString())
                    + " - "
                    + (hint == null ? "no hint" : hint.toString())
                    + " - "
                    + f.format("%10d", skipped);
        }
    }
}
