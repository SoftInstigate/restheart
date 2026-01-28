/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.restheart.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.ReadConcern;
import com.mongodb.ReadConcernLevel;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoDatabase;

/**
 * ReplicaSet Connection Options (RSOps) - A record that encapsulates MongoDB replica set configuration options.
 * <p>
 * This record provides a convenient way to manage and apply MongoDB connection options including
 * read preferences, read concerns, and write concerns. These options control how MongoDB handles
 * read and write operations in a replica set environment.
 * </p>
 * 
 * <p>RSOps instances can be created from a MongoDB connection string or constructed with specific
 * options. The options can then be applied to a MongoDatabase instance to configure its behavior.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create from connection string
 * RSOps rsOps = RSOps.from(new ConnectionString("mongodb://localhost:27017/?readPreference=secondary"));
 * 
 * // Apply to database
 * MongoDatabase configuredDb = rsOps.apply(database);
 * 
 * // Create with specific options
 * RSOps customOps = new RSOps()
 *     .withReadPreference(ReadPreference.secondaryPreferred())
 *     .withWriteConcern(WriteConcern.MAJORITY);
 * }</pre>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public record RSOps(
    /**
     * The read preference that determines which members of a replica set to read from.
     * <p>
     * Read preference can be specified in connection strings using the {@code readPreference} parameter,
     * e.g., {@code ?readPreference=primary}
     * </p>
     * 
     * <p>Allowed values (case insensitive):</p>
     * <ul>
     *   <li>{@code PRIMARY} - Read from the primary node only</li>
     *   <li>{@code SECONDARY} - Read from secondary nodes only</li>
     *   <li>{@code SECONDARY_PREFERRED} - Read from secondary nodes if available, otherwise from primary</li>
     *   <li>{@code PRIMARY_PREFERRED} - Read from primary if available, otherwise from secondary</li>
     *   <li>{@code NEAREST} - Read from the nearest node (lowest latency)</li>
     * </ul>
     * 
     * @see ReadPreference
     */
    ReadPreference readPreference,

    /**
     * The read concern level that controls the consistency and isolation properties of read operations.
     * <p>
     * Read concern can be specified in connection strings using the {@code readConcern} parameter,
     * e.g., {@code ?readConcern=majority}
     * </p>
     * 
     * <p>Allowed values (case insensitive):</p>
     * <ul>
     *   <li>{@code DEFAULT} - Use the server's default read concern</li>
     *   <li>{@code LOCAL} - Return the most recent data available on the queried node</li>
     *   <li>{@code MAJORITY} - Return data that has been acknowledged by a majority of replica set members</li>
     *   <li>{@code LINEARIZABLE} - Return data that reflects all successful writes prior to the read operation.
     *       This read concern is only compatible with {@link ReadPreference#primary()}</li>
     *   <li>{@code SNAPSHOT} - Return data from a specific point in time (requires transactions)</li>
     *   <li>{@code AVAILABLE} - Return data with no guarantee that it has been written to a majority of nodes</li>
     * </ul>
     * 
     * @see ReadConcern
     * @see ReadConcernLevel
     */
    ReadConcern readConcern,

    /**
     * The write concern that controls the acknowledgment of write operations.
     * <p>
     * Write concern can be specified in connection strings using the {@code writeConcern} parameter,
     * e.g., {@code ?writeConcern=majority}
     * </p>
     * 
     * <p>Allowed values (case insensitive):</p>
     * <ul>
     *   <li>{@code ACKNOWLEDGED} - Wait for acknowledgement using the server's default write concern.
     *       See <a href="https://docs.mongodb.com/manual/core/write-concern/#write-concern-acknowledged">MongoDB Manual</a></li>
     *   <li>{@code W1} - Wait for acknowledgement from a single member.
     *       See <a href="https://docs.mongodb.com/manual/reference/write-concern/#w-option">w option</a></li>
     *   <li>{@code W2} - Wait for acknowledgement from two members.
     *       See <a href="https://docs.mongodb.com/manual/reference/write-concern/#w-option">w option</a></li>
     *   <li>{@code W3} - Wait for acknowledgement from three members.
     *       See <a href="https://docs.mongodb.com/manual/reference/write-concern/#w-option">w option</a></li>
     *   <li>{@code UNACKNOWLEDGED} - Return immediately after sending the write to the socket.
     *       Network issues raise exceptions, but not server errors.
     *       See <a href="https://docs.mongodb.com/manual/core/write-concern/#unacknowledged">Unacknowledged</a></li>
     *   <li>{@code JOURNALED} - Wait for the server to commit the write to the journal file on disk.
     *       See <a href="https://docs.mongodb.com/manual/core/write-concern/#journaled">Journaled</a></li>
     *   <li>{@code MAJORITY} - Wait for acknowledgement from a majority of replica set members.
     *       Raises exceptions for both network issues and server errors</li>
     * </ul>
     * 
     * @see WriteConcern
     */
    WriteConcern writeConcern) {
        /**
         * Creates a new RSOps instance with all options set to null.
         * <p>
         * When applied to a database, null options will not modify the database's
         * existing configuration for that particular option.
         * </p>
         */
        public RSOps() {
            this(null, null, null);
        }

        /**
         * Creates a new RSOps instance with the specified read preference.
         * 
         * @param readPreference the read preference to set
         * @return a new RSOps instance with the updated read preference
         */
        public RSOps withReadPreference(ReadPreference readPreference) {
            return new RSOps(readPreference, readConcern, writeConcern);
        }

        /**
         * Creates a new RSOps instance with the specified read preference.
         * 
         * @param readPreference the read preference name (e.g., "PRIMARY", "SECONDARY")
         * @return a new RSOps instance with the updated read preference
         * @throws IllegalArgumentException if the read preference name is not valid
         */
        public RSOps withReadPreference(String readPreference) throws IllegalArgumentException {
            return withReadPreference(ReadPreference.valueOf(readPreference));
        }

        /**
         * Creates a new RSOps instance with the specified read concern.
         * 
         * @param readConcern the read concern to set
         * @return a new RSOps instance with the updated read concern
         */
        public RSOps withReadConcern(ReadConcern readConcern) {
            return new RSOps(readPreference, readConcern, writeConcern);
        }

        /**
         * Creates a new RSOps instance with the specified read concern level.
         * 
         * @param readConcern the read concern level name (e.g., "LOCAL", "MAJORITY")
         * @return a new RSOps instance with the updated read concern
         * @throws IllegalArgumentException if the read concern level name is not valid
         */
        public RSOps withReadConcern(String readConcern) throws IllegalArgumentException {
            return withReadConcern(new ReadConcern(ReadConcernLevel.fromString(readConcern)));
        }

        /**
         * Creates a new RSOps instance with the specified write concern.
         * 
         * @param writeConcern the write concern to set
         * @return a new RSOps instance with the updated write concern
         */
        public RSOps withWriteConcern(WriteConcern writeConcern) {
            return new RSOps(readPreference, readConcern, writeConcern);
        }

        /**
         * Creates a new RSOps instance with the specified write concern.
         * 
         * @param writeConcern the write concern name (e.g., "ACKNOWLEDGED", "MAJORITY")
         * @return a new RSOps instance with the updated write concern
         * @throws IllegalArgumentException if the write concern name is not valid
         */
        public RSOps withWriteConcern(String writeConcern) {
            return withWriteConcern(WriteConcern.valueOf(writeConcern));
        }

    /**
     * Applies all configured options (read preference, read concern, and write concern) to the given database.
     * <p>
     * This method is equivalent to calling {@link #applyReadConcern(MongoDatabase)},
     * {@link #applyReadPreference(MongoDatabase)}, and {@link #applyWriteConcern(MongoDatabase)}
     * in sequence. Only non-null options are applied.
     * </p>
     * 
     * @param db the MongoDB database to configure
     * @return a new MongoDatabase instance with the applied configuration options
     */
    public MongoDatabase apply(MongoDatabase db) {
        return applyWriteConcern(applyReadPreference(applyReadConcern(db)));
    }

    /**
     * Applies only the read preference to the given database.
     * <p>
     * If the read preference is null, the original database instance is returned unchanged.
     * </p>
     * 
     * @param db the MongoDB database to configure
     * @return a new MongoDatabase instance with the applied read preference, or the original if read preference is null
     */
    public MongoDatabase applyReadPreference(MongoDatabase db) {
        return readPreference == null ? db : db.withReadPreference(readPreference);
    }

    /**
     * Applies only the read concern to the given database.
     * <p>
     * If the read concern is null, the original database instance is returned unchanged.
     * </p>
     * 
     * @param db the MongoDB database to configure
     * @return a new MongoDatabase instance with the applied read concern, or the original if read concern is null
     */
    public MongoDatabase applyReadConcern(MongoDatabase db) {
        return readConcern == null ? db : db.withReadConcern(readConcern);
    }

    /**
     * Applies only the write concern to the given database.
     * <p>
     * If the write concern is null, the original database instance is returned unchanged.
     * </p>
     * 
     * @param db the MongoDB database to configure
     * @return a new MongoDatabase instance with the applied write concern, or the original if write concern is null
     */
    public MongoDatabase applyWriteConcern(MongoDatabase db) {
        return writeConcern == null ? db : db.withWriteConcern(writeConcern);
    }

    /**
     * Creates an RSOps instance from a MongoDB connection string.
     * <p>
     * This factory method extracts the read preference, read concern, and write concern
     * from the connection string parameters. Any options not specified in the connection
     * string will be null in the resulting RSOps instance.
     * </p>
     * 
     * <p>Example connection string with options:</p>
     * <pre>{@code
     * mongodb://host:port/db?readPreference=secondary&readConcern=majority&writeConcern=w2
     * }</pre>
     * 
     * @param mongoUri the MongoDB connection string containing the configuration options
     * @return a new RSOps instance with options extracted from the connection string
     */
    public static RSOps from(ConnectionString mongoUri) {
        return new RSOps(mongoUri.getReadPreference(), mongoUri.getReadConcern(), mongoUri.getWriteConcern());
    }
}
