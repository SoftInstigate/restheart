/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2023 SoftInstigate
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
 * ReplicaSet Connection Options
 */
public record RSOps(
    /**
     * the readPreference, e.g. ?readPreference=primary
     * allowed values are (case insensitive):
     * PRIMARY
     * SECONDARY
     * SECONDARY_PREFERRED
     * PRIMARY_PREFERRED
     * NEAREST
     */
    ReadPreference readPreference,

    /**
     * the readConcern, e.g. ?readConcern=majority
     * allowed values are (case insensitive):
     *
     * DEFAULT
     *      Use the servers default read concern.
     * LOCAL
     *      The local read concern.
     * MAJORITY
     *      The majority read concern.
     * LINEARIZABLE
     *      The linearizable read concern.
     *      This read concern is only compatible with {@link ReadPreference#primary()}.
     * SNAPSHOT
     *      The snapshot read concern.
     * AVAILABLE
     *      The available read concern.
     */
    ReadConcern readConcern,

    /**
     * the writeConcern, e.g. ?writeConcern=majority
     * allowed values are (case insensitive):
     *
     * ACKNOWLEDGED
     *      Write operations that use this write concern will wait for acknowledgement, using the default write concern configured on the server.
     *      @mongodb.driver.manual core/write-concern/#write-concern-acknowledged Acknowledged
     * W1
     *      Write operations that use this write concern will wait for acknowledgement from a single member.
     *      @mongodb.driver.manual reference/write-concern/#w-option w option
     * W2
     *      Write operations that use this write concern will wait for acknowledgement from two members.
     *      @mongodb.driver.manual reference/write-concern/#w-option w option
     * W3
     *      Write operations that use this write concern will wait for acknowledgement from three members.
     *      @mongodb.driver.manual reference/write-concern/#w-option w option
     * UNACKNOWLEDGED
     *      Write operations that use this write concern will return as soon as the message is written to the socket. Exceptions are raised for
     *      network issues, but not server errors.
     *      @mongodb.driver.manual core/write-concern/#unacknowledged Unacknowledged
     * JOURNALED
     *      Write operations wait for the server to group commit to the journal file on disk.
     *      @mongodb.driver.manual core/write-concern/#journaled Journaled
     * MAJORITY
     *      Exceptions are raised for network issues, and server errors; waits on a majority of servers for the write operation.
     */
    WriteConcern writeConcern) {
        public RSOps() {
            this(null, null, null);
        }

        public RSOps withReadPreference(ReadPreference readPreference) {
            return new RSOps(readPreference, readConcern, writeConcern);
        }

        public RSOps withReadPreference(String readPreference) throws IllegalArgumentException {
            return withReadPreference(ReadPreference.valueOf(readPreference));
        }

        public RSOps withReadConcern(ReadConcern readConcern) {
            return new RSOps(readPreference, readConcern, writeConcern);
        }

        public RSOps withReadConcern(String readConcern) throws IllegalArgumentException {
            return withReadConcern(new ReadConcern(ReadConcernLevel.fromString(readConcern)));
        }

        public RSOps withWriteConcern(WriteConcern writeConcern) {
            return new RSOps(readPreference, readConcern, writeConcern);
        }

        public RSOps withWriteConcern(String writeConcern) {
            return withWriteConcern(WriteConcern.valueOf(writeConcern));
        }

    /**
     * apply the read concern, read preference and write concern to the given db
     *
     * @param collection
     * @return
     */
    public MongoDatabase apply(MongoDatabase db) {
        return applyWriteConcern(applyReadPreference(applyReadConcern(db)));
    }

    public MongoDatabase applyReadPreference(MongoDatabase db) {
        return readPreference == null ? db : db.withReadPreference(readPreference);
    }

    public MongoDatabase applyReadConcern(MongoDatabase db) {
        return readConcern == null ? db : db.withReadConcern(readConcern);
    }

    public MongoDatabase applyWriteConcern(MongoDatabase db) {
        return writeConcern == null ? db : db.withWriteConcern(writeConcern);
    }

    public static RSOps from(ConnectionString mongoUri) {
        return new RSOps(mongoUri.getReadPreference(), mongoUri.getReadConcern(), mongoUri.getWriteConcern());
    }
}
