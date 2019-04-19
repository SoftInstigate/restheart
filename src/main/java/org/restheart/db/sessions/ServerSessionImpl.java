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
package org.restheart.db.sessions;

import com.mongodb.session.ServerSession;
import org.bson.BsonBinary;
import org.bson.BsonDocument;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ServerSessionImpl implements ServerSession {
    interface Clock {
        long millis();
    }

    private Clock clock = new Clock() {
        @Override
        public long millis() {
            return System.currentTimeMillis();
        }
    };

    private final BsonDocument identifier;
    private long transactionNumber = 0;
    private volatile long lastUsedAtMillis = clock.millis();
    private volatile boolean closed;

    ServerSessionImpl(final BsonBinary identifier) {
        this.identifier = new BsonDocument("id", identifier);
    }

    void close() {
        closed = true;
    }

    long getLastUsedAtMillis() {
        return lastUsedAtMillis;
    }

    @Override
    public long getTransactionNumber() {
        return transactionNumber;
    }

    public void setTransactionNumber(long number) {
        this.transactionNumber = number;
    }

    @Override
    public BsonDocument getIdentifier() {
        lastUsedAtMillis = clock.millis();
        return identifier;
    }

    @Override
    public long advanceTransactionNumber() {
        return transactionNumber++;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
