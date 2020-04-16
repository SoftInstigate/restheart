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
package org.restheart.mongodb.db;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import java.util.Formatter;
import java.util.Objects;
import org.bson.BsonDocument;
import org.restheart.exchange.ClientSessionImpl;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CursorPoolEntryKey {
    private final ClientSession session;
    private final MongoCollection<BsonDocument> collection;
    private final BsonDocument sort;
    private final BsonDocument filter;
    private final BsonDocument keys;
    private final BsonDocument hint;
    private final int skipped;
    private final long cursorId;

    /**
     * @param session
     * @param collection
     * @param sort
     * @param filter
     * @param keys
     * @param hint
     * @param skipped
     * @param cursorId
     */
    public CursorPoolEntryKey(
            ClientSession session,
            MongoCollection<BsonDocument> collection,
            BsonDocument sort,
            BsonDocument filter,
            BsonDocument keys,
            BsonDocument hint,
            int skipped,
            long cursorId) {
        this.session = session;
        this.collection = collection;
        this.filter = filter;
        this.keys = keys;
        this.hint = hint;
        this.sort = sort;
        this.skipped = skipped;
        this.cursorId = cursorId;
    }

    /**
     * @param key
     */
    public CursorPoolEntryKey(CursorPoolEntryKey key) {
        this.session = key.session;
        this.collection = key.collection;
        this.filter = key.filter;
        this.keys = key.keys;
        this.hint = key.hint;
        this.sort = key.sort;
        this.skipped = key.skipped;
        this.cursorId = key.cursorId;
    }

    /**
     * @return the collection
     */
    public MongoCollection<BsonDocument> getCollection() {
        return collection;
    }

    /**
     * @return the filter
     */
    public BsonDocument getFilter() {
        return filter;
    }

    /**
     * @return the sort
     */
    public BsonDocument getSort() {
        return sort;
    }

    /**
     * @return the skipped
     */
    public int getSkipped() {
        return skipped;
    }

    /**
     * @return the cursorId
     */
    public long getCursorId() {
        return cursorId;
    }

    /**
     * @return keys
     */
    public BsonDocument getKeys() {
        return keys;
    }

    /**
     * @return the hint
     */
    public BsonDocument getHint() {
        return hint;
    }

    @Override
    public int hashCode() {
        return Objects.hash(collection, filter, keys, sort, skipped, cursorId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CursorPoolEntryKey other = (CursorPoolEntryKey) obj;

        if (!Objects.equals(this.session, other.session)) {
            return false;
        }
        if (!Objects.equals(this.collection, other.collection)) {
            return false;
        }
        if (!Objects.equals(this.filter, other.filter)) {
            return false;
        }
        if (!Objects.equals(this.keys, other.keys)) {
            return false;
        }
        if (!Objects.equals(this.hint, other.hint)) {
            return false;
        }
        if (!Objects.equals(this.sort, other.sort)) {
            return false;
        }
        if (!Objects.equals(this.skipped, other.skipped)) {
            return false;
        }
        return Objects.equals(this.cursorId, other.cursorId);
    }

    @Override
    public String toString() {

        return "{ session: "
                + ClientSessionImpl.getSid(getSession())
                + ", "
                + "collection: "
                + collection.getNamespace()
                + ", "
                + "filter: "
                + (filter == null ? "null" : filter.toString())
                + ", "
                + (keys == null ? "null" : keys.toString())
                + ", "
                + (hint == null ? "null" : hint.toString())
                + ", "
                + "sort: "
                + (sort == null ? "null" : sort.toString())
                + ", "
                + "skipped: "
                + skipped + ", "
                + "cursorId: "
                + cursorId + "}";
    }

    String getCacheStatsGroup() {
        try (Formatter f = new Formatter()) {

            return (filter == null ? "no filter" : filter.toString())
                    + " - "
                    + (sort == null ? "no sort" : sort.toString())
                    + " - "
                    + (hint == null ? "no hint" : hint.toString())
                    + " - "
                    + f.format("%10d", getSkipped());
        }
    }

    /**
     * @return the session
     */
    public ClientSession getSession() {
        return session;
    }
}
