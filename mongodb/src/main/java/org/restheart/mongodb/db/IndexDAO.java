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

import com.mongodb.MongoClient;
import com.mongodb.client.ClientSession;
import com.mongodb.client.ListIndexesIterable;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.IndexOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import static org.restheart.exchange.ExchangeKeys.DB_META_DOCID;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
class IndexDAO {

    public static final Bson METADATA_QUERY
            = eq("_id", DB_META_DOCID);

    private static final BsonDocument FIELDS_TO_RETURN_INDEXES;

    static {
        FIELDS_TO_RETURN_INDEXES = new BsonDocument();
        FIELDS_TO_RETURN_INDEXES.put("key", new BsonInt32(1));
        FIELDS_TO_RETURN_INDEXES.put("name", new BsonInt32(1));
    }
    private final MongoClient client;

    IndexDAO(MongoClient client) {
        this.client = client;
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @return
     */
    List<BsonDocument> getCollectionIndexes(
            final ClientSession cs,
            final String dbName,
            final String collName) {
        List<BsonDocument> ret = new ArrayList<>();

        ListIndexesIterable<Document> indexes = cs == null
                ? client.getDatabase(dbName)
                        .getCollection(collName, BsonDocument.class)
                        .listIndexes()
                : client.getDatabase(dbName)
                        .getCollection(collName, BsonDocument.class)
                        .listIndexes(cs);

        indexes.iterator().forEachRemaining(
                i -> {
                    BsonDocument bi = BsonDocument.parse(i.toJson());

                    BsonValue name = bi.remove("name");
                    bi.put("_id", name);

                    ret.add(bi);
                });

        return ret;
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collection
     * @param keys
     * @param options
     */
    void createIndex(
            final ClientSession cs,
            final String dbName,
            final String collection,
            final BsonDocument keys,
            final BsonDocument options) {
        if (options == null) {
            if (cs == null) {
                client.getDatabase(dbName)
                        .getCollection(collection)
                        .createIndex(keys);
            } else {
                client.getDatabase(dbName)
                        .getCollection(collection)
                        .createIndex(cs, keys);
            }
        } else {
            if (cs == null) {
                client.getDatabase(dbName)
                        .getCollection(collection)
                        .createIndex(keys, getIndexOptions(options));
            } else {
                client.getDatabase(dbName)
                        .getCollection(collection)
                        .createIndex(cs, keys, getIndexOptions(options));
            }
        }
    }

    /**
     *
     * @param cs the client session
     * @param db
     * @param collection
     * @param indexId
     * @return
     */
    int deleteIndex(
            final ClientSession cs,
            final String dbName,
            final String collection,
            final String indexId) {
        if (cs == null) {
            client.getDatabase(dbName)
                    .getCollection(collection)
                    .dropIndex(indexId);
        } else {
            client.getDatabase(dbName)
                    .getCollection(collection)
                    .dropIndex(cs, indexId);
        }

        return HttpStatus.SC_NO_CONTENT;
    }

    IndexOptions getIndexOptions(final BsonDocument options) {
        IndexOptions ret = new IndexOptions();

        //***Options for All Index Types
        //name  string
        if (options.containsKey("name")
                && options.get("name").isString()) {
            ret.name(options.get("name").asString().getValue());
        }

        //background    boolean
        if (options.containsKey("background")
                && options.get("background").isBoolean()) {
            ret.background(options.get("background").asBoolean().getValue());
        }

        //expireAfterSeconds    integer
        if (options.containsKey("expireAfterSeconds")
                && options.get("expireAfterSeconds").isInt32()) {
            ret.expireAfter(0l + options.get("expireAfterSeconds")
                    .asInt32().getValue(),
                    TimeUnit.SECONDS
            );
        }

        //partialFilterExpression   document
        if (options.containsKey("partialFilterExpression")
                && options.get("partialFilterExpression").isDocument()) {
            ret.partialFilterExpression(options.get("partialFilterExpression")
                    .asDocument());
        }

        //storageEngine document
        if (options.containsKey("storageEngine")
                && options.get("storageEngine").isDocument()) {
            ret.storageEngine(options.get("storageEngine")
                    .asDocument());
        }

        //unique   boolean
        if (options.containsKey("unique")
                && options.get("unique").isBoolean()) {
            ret.unique(options.get("unique")
                    .asBoolean().getValue());
        }

        //sparse    boolean
        if (options.containsKey("sparse")
                && options.get("sparse").isBoolean()) {
            ret.sparse(options.get("sparse")
                    .asBoolean().getValue());
        }

        //***Options for text Indexes
        //weights	document
        if (options.containsKey("weights")
                && options.get("weights").isDocument()) {
            ret.weights(options.get("weights")
                    .asDocument());
        }
        //default_language	string
        if (options.containsKey("default_language")
                && options.get("default_language").isString()) {
            ret.defaultLanguage(options.get("default_language")
                    .asString().getValue());
        }

        //language_override	string
        if (options.containsKey("language_override")
                && options.get("language_override").isString()) {
            ret.languageOverride(options.get("language_override")
                    .asString().getValue());
        }

        //textIndexVersion	integer
        if (options.containsKey("textIndexVersion")
                && options.get("textIndexVersion").isInt32()) {
            ret.textVersion(options.get("textIndexVersion")
                    .asInt32().getValue());
        }

        //***Options for 2dsphere Indexes
        //2dsphereIndexVersion	integer
        if (options.containsKey("2dsphereIndexVersion")
                && options.get("2dsphereIndexVersion").isInt32()) {
            ret.sphereVersion(options.get("2dsphereIndexVersion")
                    .asInt32().getValue());
        }

        //***Options for 2d Indexes
        //bits	integer
        if (options.containsKey("bits")
                && options.get("bits").isInt32()) {
            ret.bits(options.get("bits")
                    .asInt32().getValue());
        }

        //min	number
        if (options.containsKey("min")
                && options.get("min").isDouble()) {
            ret.min(options.get("min")
                    .asDouble().getValue());
        }

        //max	number
        if (options.containsKey("max")
                && options.get("max").isDouble()) {
            ret.max(options.get("max")
                    .asDouble().getValue());
        }

        //***Options for geoHaystack Indexes
        //bucketSize	number
        if (options.containsKey("bucketSize")
                && options.get("bucketSize").isDouble()) {
            ret.bucketSize(options.get("bucketSize")
                    .asDouble().getValue());
        }

        return ret;
    }
}
