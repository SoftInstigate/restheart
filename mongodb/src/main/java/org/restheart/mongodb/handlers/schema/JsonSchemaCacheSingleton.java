/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
package org.restheart.mongodb.handlers.schema;

import static com.mongodb.client.model.Filters.eq;
import java.util.Objects;
import java.util.Optional;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import static org.restheart.exchange.ExchangeKeys._SCHEMAS;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.mongodb.db.Databases;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class JsonSchemaCacheSingleton {

    private static final String SEPARATOR = "_@_@_";
    private static final long MAX_CACHE_SIZE = 1_000;
    static final Logger LOGGER
            = LoggerFactory.getLogger(JsonSchemaCacheSingleton.class);

    /**
     *
     * @return
     */
    public static JsonSchemaCacheSingleton getInstance() {
        return CachesSingletonHolder.INSTANCE;

    }
    private final Databases dbs = Databases.get();;

    private Cache<String, Schema> schemaCache = null;
    private Cache<String, BsonDocument> rawSchemaCache = null;

    JsonSchemaCacheSingleton() {
        if (MongoServiceConfiguration.get().isSchemaCacheEnabled()) {
            this.schemaCache = CacheFactory.createLocalCache(MAX_CACHE_SIZE,
                    Cache.EXPIRE_POLICY.AFTER_WRITE,
                    MongoServiceConfiguration.get().getSchemaCacheTtl());

            this.rawSchemaCache = CacheFactory.createLocalCache(MAX_CACHE_SIZE,
                    Cache.EXPIRE_POLICY.AFTER_WRITE,
                    MongoServiceConfiguration.get().getSchemaCacheTtl());
        }
    }

    /**
     *
     * @param schemaStoreDb
     * @param schemaId
     * @return
     * @throws JsonSchemaNotFoundException
     */
    public Schema get(
            String schemaStoreDb,
            BsonValue schemaId)
            throws JsonSchemaNotFoundException {
        if (MongoServiceConfiguration.get().isSchemaCacheEnabled()) {
            Optional<Schema> _schema = schemaCache.get(
                    schemaStoreDb
                    + SEPARATOR
                    + schemaId);

            if (_schema != null && _schema.isPresent()) {
                return _schema.get();
            } else {
                // load it
                Schema s = load(schemaStoreDb, schemaId);

                schemaCache.put(schemaStoreDb + SEPARATOR + schemaId, s);

                return s;
            }
        } else {
            return load(schemaStoreDb, schemaId);
        }
    }

    /**
     *
     * @param schemaStoreDb
     * @param schemaId
     * @return
     * @throws JsonSchemaNotFoundException
     */
    public BsonDocument getRaw(
            String schemaStoreDb,
            BsonValue schemaId)
            throws JsonSchemaNotFoundException {
        if (MongoServiceConfiguration.get().isSchemaCacheEnabled()) {
            Optional<BsonDocument> _schema
                    = rawSchemaCache.get(schemaStoreDb + SEPARATOR + schemaId);

            if (_schema != null && _schema.isPresent()) {
                return _schema.get();
            } else {
                // load it
                BsonDocument s = loadRaw(schemaStoreDb, schemaId);

                rawSchemaCache.put(schemaStoreDb + SEPARATOR + schemaId, s);

                return s;
            }
        } else {
            return loadRaw(schemaStoreDb, schemaId);
        }
    }

    private Schema load(
            String schemaStoreDb,
            BsonValue schemaId)
            throws JsonSchemaNotFoundException {
        BsonDocument document = loadRaw(schemaStoreDb, schemaId);

        return SchemaLoader.load(
                new JSONObject(document.toJson()), new SchemaStoreClient());
    }

    private BsonDocument loadRaw(String schemaStoreDb, BsonValue schemaId) throws JsonSchemaNotFoundException {
        var document = dbs.collection(Optional.empty(), schemaStoreDb, _SCHEMAS)
            .find(eq("_id", schemaId))
            .first();

        if (Objects.isNull(document)) {
            var sid = BsonUtils.getIdAsString(schemaId, false);

            throw new JsonSchemaNotFoundException(
                    "schema not found "
                    + schemaStoreDb
                    + "/" + sid);
        }

        // schemas are stored with escaped keys, need to unescape them
        JsonSchemaTransformer.unescapeSchema(document);

        return document;
    }

    private static class CachesSingletonHolder {

        private static final JsonSchemaCacheSingleton INSTANCE
                = new JsonSchemaCacheSingleton();

        private CachesSingletonHolder() {
        }
    }
}
