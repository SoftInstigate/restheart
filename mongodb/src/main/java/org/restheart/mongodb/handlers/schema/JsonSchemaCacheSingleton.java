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
import static org.restheart.handlers.exchange.ExchangeKeys._SCHEMAS;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.mongodb.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
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
    private final DatabaseImpl dbsDAO;

    private Cache<String, Schema> schemaCache = null;
    private Cache<String, BsonDocument> rawSchemaCache = null;

    JsonSchemaCacheSingleton() {
        dbsDAO = new DatabaseImpl();

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

    private BsonDocument loadRaw(
            String schemaStoreDb,
            BsonValue schemaId)
            throws JsonSchemaNotFoundException {
        BsonDocument document = dbsDAO
                .getCollection(schemaStoreDb, _SCHEMAS)
                .find(eq("_id", schemaId))
                .first();

        if (Objects.isNull(document)) {
            String sid;

            sid = JsonUtils.getIdAsString(schemaId, false);

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
