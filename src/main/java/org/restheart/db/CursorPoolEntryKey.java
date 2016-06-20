/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.db;

import com.mongodb.client.MongoCollection;
import java.util.Formatter;
import java.util.Objects;
import org.bson.BsonDocument;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CursorPoolEntryKey {

    private final MongoCollection collection;
    private final BsonDocument sort;
    private final BsonDocument filter;
    private final BsonDocument keys;
    private final int skipped;
    private final long cursorId;

    public CursorPoolEntryKey(
            MongoCollection collection,
            BsonDocument sort,
            BsonDocument filter,
            BsonDocument keys,
            int skipped,
            long cursorId) {
        this.collection = collection;
        this.filter = filter;
        this.keys = keys;
        this.sort = sort;
        this.skipped = skipped;
        this.cursorId = cursorId;
    }

    /**
     * @return the collection
     */
    public MongoCollection getCollection() {
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
        if (!Objects.equals(this.collection, other.collection)) {
            return false;
        }
        if (!Objects.equals(this.filter, other.filter)) {
            return false;
        }
        if (!Objects.equals(this.keys, other.keys)) {
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
        return "{ collection: "
                + collection.getNamespace()
                + ", "
                + "filter: "
                + (filter == null ? "null" : filter.toString())
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
        Formatter f = new Formatter();

        return (filter == null ? "no filter" : filter.toString())
                + " - "
                + (sort == null ? "no sort_by" : sort.toString())
                + " - "
                + f.format("%10d", getSkipped());
    }

}
