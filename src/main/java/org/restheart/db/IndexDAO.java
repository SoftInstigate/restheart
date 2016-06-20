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

import com.mongodb.MongoClient;
import com.mongodb.client.ListIndexesIterable;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.IndexOptions;
import java.util.ArrayList;
import org.restheart.utils.HttpStatus;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
class IndexDAO {

    private final MongoClient client;

    public static final Bson METADATA_QUERY 
            = eq("_id", "_properties");

    private static final BsonDocument FIELDS_TO_RETURN_INDEXES;

    static {
        FIELDS_TO_RETURN_INDEXES = new BsonDocument();
        FIELDS_TO_RETURN_INDEXES.put("key", new BsonInt32(1));
        FIELDS_TO_RETURN_INDEXES.put("name", new BsonInt32(1));
    }

    IndexDAO(MongoClient client) {
        this.client = client;
    }

    /**
     *
     * @param dbName
     * @param collName
     * @return
     */
    List<BsonDocument> getCollectionIndexes(String dbName, String collName) {
        List<BsonDocument> ret = new ArrayList<>();

        ListIndexesIterable<Document> indexes = client
                .getDatabase(dbName)
                .getCollection(collName, BsonDocument.class)
                .listIndexes();

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
     * @param dbName
     * @param collection
     * @param keys
     * @param options
     */
    void createIndex(
            String dbName,
            String collection,
            BsonDocument keys,
            BsonDocument options) {
        if (options == null) {
            client
                    .getDatabase(dbName)
                    .getCollection(collection)
                    .createIndex(keys);
        } else {
            // need to find a way to get IndexOptions from json
            IndexOptions io = new IndexOptions();

            io.background(true);

            client
                    .getDatabase(dbName)
                    .getCollection(collection)
                    .createIndex(keys, getIndexOptions(options));
        }
    }

    /**
     *
     * @param db
     * @param collection
     * @param indexId
     * @return
     */
    int deleteIndex(
            String dbName,
            String collection,
            String indexId) {
        client
                .getDatabase(dbName)
                .getCollection(collection)
                .dropIndex(indexId);

        return HttpStatus.SC_NO_CONTENT;
    }

    IndexOptions getIndexOptions(BsonDocument options) {
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
