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
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.restheart.polyglot.spi.proxy.BsonProxy;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

/**
 * The {@code mongo} capability of the SDK, over the {@code mclient} provider: plain values in,
 * plain values out, with the names a Node or PyMongo developer already has in their fingers.
 *
 * <p>{@code mongo.db(name)} is a {@link RhDb}, {@code mongo.raw()} the {@link MongoClient}
 * itself, for what the facade does not cover. Sessions, transactions and change streams are not
 * covered: the driver is one call away.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhMongo {
    private final MongoClient client;

    /**
     * @param client the connected client
     */
    public RhMongo(MongoClient client) {
        this.client = client;
    }

    /**
     * @param name the database name
     * @return the database
     */
    public RhDb db(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("database name is required");
        }
        return new RhDb(client.getDatabase(name));
    }

    /**
     * @return the names of the databases
     */
    public Object databases() {
        var names = new ArrayList<Object>();
        client.listDatabaseNames().into(names);
        return ProxyArray.fromList(names);
    }

    /**
     * @return the {@link MongoClient}, the escape hatch
     */
    public MongoClient raw() {
        return client;
    }

    /**
     * A database: {@code collection(name)} and {@code command(doc)}.
     */
    public static class RhDb {
        private final MongoDatabase db;

        RhDb(MongoDatabase db) {
            this.db = db;
        }

        public String name() {
            return db.getName();
        }

        public RhCollection collection(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("collection name is required");
            }
            return new RhCollection(db.getCollection(name, BsonDocument.class));
        }

        /**
         * Runs a database command, {@code createIndexes}, {@code collStats}, {@code explain},
         * anything the server understands, so the facade never has to grow a method for each.
         *
         * @param command the command document
         * @return the result document
         */
        public Object command(Value command) {
            var cmd = GuestValues.toBson(command);
            if (!cmd.isDocument()) {
                throw new IllegalArgumentException("command must be a document");
            }
            return BsonProxy.toGuest(db.runCommand(cmd.asDocument(), BsonDocument.class));
        }

        public Object collections() {
            var names = new ArrayList<Object>();
            db.listCollectionNames().into(names);
            return ProxyArray.fromList(names);
        }

        public void drop() {
            db.drop();
        }

        public MongoDatabase raw() {
            return db;
        }
    }
}
