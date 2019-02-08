package org.restheart.handlers.schema;

import static com.mongodb.client.model.Filters.eq;
import java.util.Objects;
import java.util.Optional;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.restheart.Bootstrapper;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.db.DatabaseImpl;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.JsonUtils;
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

        if (Bootstrapper.getConfiguration().isSchemaCacheEnabled()) {
            this.schemaCache = CacheFactory.createLocalCache(MAX_CACHE_SIZE,
                    Cache.EXPIRE_POLICY.AFTER_WRITE,
                    Bootstrapper.getConfiguration().getSchemaCacheTtl());

            this.rawSchemaCache = CacheFactory.createLocalCache(MAX_CACHE_SIZE,
                    Cache.EXPIRE_POLICY.AFTER_WRITE,
                    Bootstrapper.getConfiguration().getSchemaCacheTtl());
        }
    }

    public Schema get(
            String schemaStoreDb,
            BsonValue schemaId)
            throws JsonSchemaNotFoundException {
        if (Bootstrapper.getConfiguration().isSchemaCacheEnabled()) {
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

    public BsonDocument getRaw(
            String schemaStoreDb,
            BsonValue schemaId)
            throws JsonSchemaNotFoundException {
        if (Bootstrapper.getConfiguration().isSchemaCacheEnabled()) {
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
                .getCollection(schemaStoreDb, RequestContext._SCHEMAS)
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
