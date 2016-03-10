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
package org.restheart.handlers.schema;

import com.mongodb.DBObject;
import java.util.Objects;
import java.util.Optional;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.restheart.Bootstrapper;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.db.DbsDAO;
import org.restheart.handlers.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonSchemaCacheSingleton {
    private final DbsDAO dbsDAO;

    private static final String SEPARATOR = "_@_@_";
    private static final long MAX_CACHE_SIZE = 1_000;

    private Cache<String, Schema> schemaCache = null;
    private Cache<String, DBObject> rawSchemaCache = null;
    
    static final Logger LOGGER = LoggerFactory.getLogger(JsonSchemaCacheSingleton.class);

    JsonSchemaCacheSingleton() {
        dbsDAO = new DbsDAO();

        if (Bootstrapper.getConfiguration().isSchemaCacheEnabled()) {
            this.schemaCache = CacheFactory.createLocalCache(MAX_CACHE_SIZE,
                    Cache.EXPIRE_POLICY.AFTER_WRITE,
                    Bootstrapper.getConfiguration().getSchemaCacheTtl());
            
            this.rawSchemaCache = CacheFactory.createLocalCache(MAX_CACHE_SIZE,
                    Cache.EXPIRE_POLICY.AFTER_WRITE,
                    Bootstrapper.getConfiguration().getSchemaCacheTtl());
        }
    }

    public Schema get(String schemaStoreDb, Object schemaId) throws JsonSchemaNotFoundException {
        if (Bootstrapper.getConfiguration().isSchemaCacheEnabled()) {
            Optional<Schema> _schema = schemaCache.get(schemaStoreDb + SEPARATOR + schemaId);

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
    
    public DBObject getRaw(String schemaStoreDb, Object schemaId) throws JsonSchemaNotFoundException {
        if (Bootstrapper.getConfiguration().isSchemaCacheEnabled()) {
            Optional<DBObject> _schema = rawSchemaCache.get(schemaStoreDb + SEPARATOR + schemaId);

            if (_schema != null && _schema.isPresent()) {
                return _schema.get();
            } else {
                // load it
                DBObject s = loadRaw(schemaStoreDb, schemaId);

                rawSchemaCache.put(schemaStoreDb + SEPARATOR + schemaId, s);

                return s;
            }
        } else {
            return loadRaw(schemaStoreDb, schemaId);
        }
    }

    private Schema load(String schemaStoreDb, Object schemaId) throws JsonSchemaNotFoundException {
        DBObject document = loadRaw(schemaStoreDb, schemaId);

        return SchemaLoader.load(new JSONObject(document.toMap()), new SchemaStoreClient());
    }
    
    private DBObject loadRaw(String schemaStoreDb, Object schemaId) throws JsonSchemaNotFoundException {
        DBObject document = dbsDAO.getCollectionLegacy(schemaStoreDb, RequestContext._SCHEMAS).findOne(schemaId);

        if (Objects.isNull(document)) {
            throw new JsonSchemaNotFoundException("schema not found " + schemaStoreDb + "/" + schemaId);
        }

        // schemas are stored with escaped keys, need to unescape them
        JsonSchemaTransformer.unescapeSchema(document);

        return document;
    }

    /**
     *
     * @return
     */
    public static JsonSchemaCacheSingleton getInstance() {
        return CachesSingletonHolder.INSTANCE;

    }

    private static class CachesSingletonHolder {
        private static final JsonSchemaCacheSingleton INSTANCE = new JsonSchemaCacheSingleton();

        private CachesSingletonHolder() {
        }
    }
}
