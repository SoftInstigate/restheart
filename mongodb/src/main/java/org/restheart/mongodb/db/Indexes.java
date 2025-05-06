/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.IndexOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.conversions.Bson;
import static org.restheart.exchange.ExchangeKeys.DB_META_DOCID;

import org.restheart.mongodb.RSOps;
import org.restheart.utils.HttpStatus;

import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationAlternate;
import com.mongodb.client.model.CollationCaseFirst;
import com.mongodb.client.model.CollationMaxVariable;
import com.mongodb.client.model.CollationStrength;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
class Indexes {
    public static final Bson METADATA_QUERY = eq("_id", DB_META_DOCID);

    private static final BsonDocument FIELDS_TO_RETURN_INDEXES;

    static {
        FIELDS_TO_RETURN_INDEXES = new BsonDocument();
        FIELDS_TO_RETURN_INDEXES.put("key", new BsonInt32(1));
        FIELDS_TO_RETURN_INDEXES.put("name", new BsonInt32(1));
    }

    private final Collections collections = Collections.get();

    private Indexes() {
    }

    private static final Indexes INSTANCE = new Indexes();

    public static Indexes get() {
        return INSTANCE;
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName
     * @return
     */
    List<BsonDocument> getCollectionIndexes(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName) {
        var ret = new ArrayList<BsonDocument>();

        var indexes = cs.isPresent()
                ? collections.collection(rsOps, dbName, collName).listIndexes(cs.get())
                : collections.collection(rsOps, dbName, collName).listIndexes();

        indexes.iterator().forEachRemaining(i -> {
            var bi = BsonDocument.parse(i.toJson());
            var name = bi.remove("name");
            bi.put("_id", name);
            ret.add(bi);
        });

        return ret;
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collection
     * @param keys
     * @param options
     */
    void createIndex(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collection,
        final BsonDocument keys,
        final Optional<BsonDocument> options) {
        if (options.isPresent()) {
            if (cs.isPresent()) {
                collections.collection(rsOps, dbName, collection).createIndex(cs.get(), keys, getIndexOptions(options.get()));
            } else {
                collections.collection(rsOps, dbName, collection).createIndex(keys, getIndexOptions(options.get()));
            }
        } else {
            if (cs.isPresent()) {
                collections.collection(rsOps, dbName, collection).createIndex(cs.get(), keys);
            } else {
                collections.collection(rsOps, dbName, collection).createIndex(keys);
            }
        }
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collection
     * @param indexId
     * @return the HTTP status code
     */
    int deleteIndex(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collection,
        final String indexId) {
        if (cs.isPresent()) {
            collections.collection(rsOps, dbName, collection).dropIndex(cs.get(), indexId);
        } else {
            collections.collection(rsOps, dbName, collection).dropIndex(indexId);
        }

        return HttpStatus.SC_NO_CONTENT;
    }

    @SuppressWarnings("deprecation")
    IndexOptions getIndexOptions(final BsonDocument options) {
        var ret = new IndexOptions();

        //***Options for All Index Types
        //name  string
        if (options.containsKey("name") && options.get("name").isString()) {
            ret.name(options.get("name").asString().getValue());
        }

        //background    boolean
        if (options.containsKey("background") && options.get("background").isBoolean()) {
            ret.background(options.get("background").asBoolean().getValue());
        }

        //expireAfterSeconds    integer
        if (options.containsKey("expireAfterSeconds") && options.get("expireAfterSeconds").isInt32()) {
            ret.expireAfter(0l + options.get("expireAfterSeconds").asInt32().getValue(), TimeUnit.SECONDS);
        }

        //partialFilterExpression   document
        if (options.containsKey("partialFilterExpression") && options.get("partialFilterExpression").isDocument()) {
            ret.partialFilterExpression(options.get("partialFilterExpression").asDocument());
        }

        //storageEngine document
        if (options.containsKey("storageEngine") && options.get("storageEngine").isDocument()) {
            ret.storageEngine(options.get("storageEngine").asDocument());
        }

        //unique   boolean
        if (options.containsKey("unique") && options.get("unique").isBoolean()) {
            ret.unique(options.get("unique").asBoolean().getValue());
        }

        //sparse    boolean
        if (options.containsKey("sparse") && options.get("sparse").isBoolean()) {
            ret.sparse(options.get("sparse").asBoolean().getValue());
        }

        //***Options for text Indexes
        //weights	document
        if (options.containsKey("weights") && options.get("weights").isDocument()) {
            ret.weights(options.get("weights").asDocument());
        }
        //default_language	string
        if (options.containsKey("default_language") && options.get("default_language").isString()) {
            ret.defaultLanguage(options.get("default_language").asString().getValue());
        }

        //language_override	string
        if (options.containsKey("language_override")&& options.get("language_override").isString()) {
            ret.languageOverride(options.get("language_override").asString().getValue());
        }

        //textIndexVersion	integer
        if (options.containsKey("textIndexVersion")&& options.get("textIndexVersion").isInt32()) {
            ret.textVersion(options.get("textIndexVersion").asInt32().getValue());
        }

        //***Options for 2dsphere Indexes
        //2dsphereIndexVersion	integer
        if (options.containsKey("2dsphereIndexVersion") && options.get("2dsphereIndexVersion").isInt32()) {
            ret.sphereVersion(options.get("2dsphereIndexVersion").asInt32().getValue());
        }

        //***Options for 2d Indexes
        //bits	integer
        if (options.containsKey("bits")&& options.get("bits").isInt32()) {
            ret.bits(options.get("bits").asInt32().getValue());
        }

        //min	number
        if (options.containsKey("min") && options.get("min").isDouble()) {
            ret.min(options.get("min").asDouble().getValue());
        }

        //max	number
        if (options.containsKey("max") && options.get("max").isDouble()) {
            ret.max(options.get("max").asDouble().getValue());
        }

        //***Options for collation
        //collation	document
        if (options.containsKey("collation")&& options.get("collation").isDocument()) {
            ret.collation(collation(options.get("collation").asDocument()));
        }

        return ret;
    }

    private Collation collation(BsonDocument specs) {
        var builder = Collation.builder();

        if (specs.containsKey("caseLevel") && specs.get("caseLevel").isBoolean()) {
            builder.caseLevel(specs.get("caseLevel").asBoolean().getValue());
        }

        if (specs.containsKey("backwards") && specs.get("backwards").isBoolean()) {
            builder.backwards(specs.get("backwards").asBoolean().getValue());
        }

        if (specs.containsKey("normalization") && specs.get("normalization").isBoolean()) {
            builder.normalization(specs.get("normalization").asBoolean().getValue());
        }

        if (specs.containsKey("numericOrdering") && specs.get("numericOrdering").isBoolean()) {
            builder.numericOrdering(specs.get("numericOrdering").asBoolean().getValue());
        }

        if (specs.containsKey("locale") && specs.get("locale").isString()) {
            builder.locale(specs.get("locale").asString().getValue());
        }

        try {
            if (specs.containsKey("collationAlternate") && specs.get("collationAlternate").isString()) {
                builder.collationAlternate(CollationAlternate.fromString(specs.get("collationAlternate").asString().getValue()));
            }
        } catch(IllegalArgumentException iae) {
            // nothing to do
        }

        try {
            if (specs.containsKey("collationCaseFirst") && specs.get("collationCaseFirst").isString()) {
                builder.collationCaseFirst(CollationCaseFirst.fromString(specs.get("collationCaseFirst").asString().getValue()));
            }
        } catch(IllegalArgumentException iae) {
            // nothing to do
        }

        try {
            if (specs.containsKey("collationCaseFirst") && specs.get("collationCaseFirst").isString()) {
                builder.collationCaseFirst(CollationCaseFirst.fromString(specs.get("collationCaseFirst").asString().getValue()));
            }
        } catch(IllegalArgumentException iae) {
            // nothing to do
        }

        try {
            if (specs.containsKey("collationMaxVariable") && specs.get("collationMaxVariable").isString()) {
                builder.collationMaxVariable(CollationMaxVariable.fromString(specs.get("collationMaxVariable").asString().getValue()));
            }
        } catch(IllegalArgumentException iae) {
            // nothing to do
        }

        try {
            if (specs.containsKey("collationStrength") && specs.get("collationStrength").isInt32()) {
                builder.collationStrength(CollationStrength.fromInt(specs.get("collationStrength").asInt32().getValue()));
            }
        } catch(IllegalArgumentException iae) {
            // nothing to do
        }

        return builder.build();
    }
}
