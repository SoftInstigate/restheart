/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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

import com.mongodb.DBCollection;
import java.util.Deque;
import java.util.Formatter;
import java.util.Objects;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DBCursorPoolEntryKey {
    private final DBCollection collection;
    private final Deque<String> sort;
    private final Deque<String> filter;
    private final int skipped;
    private final long cursorId;

    public DBCursorPoolEntryKey(DBCollection collection, Deque<String> sort, Deque<String> filter, int skipped, long cursorId) {
        this.collection = collection;
        this.filter = filter;
        this.sort = sort;
        this.skipped = skipped;
        this.cursorId = cursorId;
    }

    /**
     * @return the collection
     */
    public DBCollection getCollection() {
        return collection;
    }

    /**
     * @return the filter
     */
    public Deque<String> getFilter() {
        return filter;
    }

    /**
     * @return the sort
     */
    public Deque<String> getSort() {
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
    
    @Override
    public int hashCode() {
        return Objects.hash(collection, filter, sort, skipped, cursorId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DBCursorPoolEntryKey other = (DBCursorPoolEntryKey) obj;
        if (!Objects.equals(this.collection, other.collection)) {
            return false;
        }
        if (!Objects.equals(this.filter, other.filter)) {
            return false;
        }
        if (!Objects.equals(this.sort, other.sort)) {
            return false;
        }
        if (!Objects.equals(this.skipped, other.skipped)) {
            return false;
        }
        if (!Objects.equals(this.cursorId, other.cursorId)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "{ collection: " + collection.getFullName() + ", " +
                "filter: " + (filter == null ? "null": filter.toString()) + ", " + 
                "sort: " + (sort == null ? "null": sort.toString()) + ", "  +
                "skipped: " + skipped + ", "  +
                "cursorId: " + cursorId + "}"; 
    }
    
    String getCacheStatsGroup() {
        Formatter f = new Formatter();
        
        return (filter == null ? "no filter" : filter.toString()) + " - " + (sort == null ? "no sort_by" : sort.toString()) + " - " + f.format("%10d", getSkipped());
    }
}