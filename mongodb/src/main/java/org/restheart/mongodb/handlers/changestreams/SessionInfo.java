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
package org.restheart.mongodb.handlers.changestreams;

import java.util.Objects;
import org.restheart.exchange.MongoRequest;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SessionInfo {

    private final String db;
    private final String collection;
    private final String changeStreamOperation;

    public SessionInfo(MongoRequest request) {
        this.db = request.getDBName();
        this.collection = request.getCollectionName();
        this.changeStreamOperation = request.getChangeStreamOperation();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getDb(), getCollection(), getChangeStreamOperation());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SessionKey)) {
            return false;
        } else {
            return obj.hashCode() == this.hashCode();
        }
    }

    @Override
    public String toString() {
        return "" + hashCode();
    }

    /**
     * @return the db
     */
    public String getDb() {
        return db;
    }

    /**
     * @return the collection
     */
    public String getCollection() {
        return collection;
    }

    /**
     * @return the changeStreamOperation
     */
    public String getChangeStreamOperation() {
        return changeStreamOperation;
    }
}
