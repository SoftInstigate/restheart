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
package org.restheart.hal.metadata.singletons;

import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import java.util.Objects;
import java.util.Optional;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.restheart.Bootstrapper;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.db.DbsDAO;
import org.restheart.hal.UnsupportedDocumentIdException;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class JsonSchemaChecker implements Checker {
    public static final String SCHEMA_STORE_DB_PROPERTY = "db";
    public static final String SCHEMA_ID_PROPERTY = "id";

    static final Logger LOGGER = LoggerFactory.getLogger(JsonSchemaChecker.class);

    @Override
    public boolean check(HttpServerExchange exchange, RequestContext context, DBObject args) {
        boolean patching = context.getMethod() == RequestContext.METHOD.PATCH;

        if (patching) {
            throw new RuntimeException("json schema checking on PATCH requests not yet implemented");
        }

        Objects.requireNonNull(args, "missing metadata property 'args'");

        Object _schemaStoreDb = args.get(SCHEMA_STORE_DB_PROPERTY);
        String schemaStore;

        Object schemaId = args.get(SCHEMA_ID_PROPERTY);

        Objects.requireNonNull(schemaId, "missing property '" + SCHEMA_ID_PROPERTY + "' in metadata property 'args'");

        if (_schemaStoreDb == null) {
            // if not specified assume the current db as the schema store db
            schemaStore = context.getDBName();
        } else if (_schemaStoreDb instanceof String) {
            schemaStore = (String) _schemaStoreDb;
        } else {
            throw new IllegalArgumentException("property " + SCHEMA_STORE_DB_PROPERTY + " in metadata 'args' must be a a string");
        }

        try {
            URLUtils.checkId(schemaId);
        } catch (UnsupportedDocumentIdException ex) {
            throw new IllegalArgumentException("wrong schema 'id' is not a valid id, ex");
        }

        try {
            Schema theschema = CacheSingleton.getInstance().get(schemaStore, schemaId);

            if (Objects.isNull(theschema)) {
                throw new IllegalArgumentException("cannot validate, schema " + schemaStore + "/" + RequestContext._SCHEMAS + "/" + schemaId.toString() + " not found");
            }

            theschema.validate(
                    new JSONObject(context.getContent().toString()));
        } catch (ValidationException ve) {
            context.addWarning(ve.getMessage());
            ve.getCausingExceptions().stream()
                    .map(ValidationException::getMessage)
                    .forEach(context::addWarning);

            return false;
        }

        return true;
    }
}

class CacheSingleton {
    private final DbsDAO dbsDAO;

    private static final String SEPARATOR = "_@_@_";
    private static final long MAX_CACHE_SIZE = 1_000;

    private LoadingCache<String, Schema> schemaCache = null;

    CacheSingleton() {
        dbsDAO = new DbsDAO();

        if (Bootstrapper.getConfiguration().isSchemaCacheEnabled()) {
            this.schemaCache = CacheFactory.createLocalLoadingCache(MAX_CACHE_SIZE,
                    Cache.EXPIRE_POLICY.AFTER_WRITE,
                    Bootstrapper.getConfiguration().getSchemaCacheTtl(),
                    (String key) -> {
                        String[] schemaStoreDbAndSchemaId = key.split(SEPARATOR);
                        return load(schemaStoreDbAndSchemaId[0], schemaStoreDbAndSchemaId[1]);
                    });
        }
    }

    public Schema get(String schemaStoreDb, Object schemaId) {
        if (Bootstrapper.getConfiguration().isSchemaCacheEnabled()) {
            Optional<Schema> _schema = schemaCache.getLoading(schemaStoreDb + SEPARATOR + schemaId);

            if (_schema != null && _schema.isPresent()) {
                return _schema.get();
            } else {
                return null;
            }
        } else {
            return load(schemaStoreDb, schemaId);
        }
    }

    private Schema load(String schemaStoreDb, Object schemaId) {
        DBObject document = dbsDAO.getCollection(schemaStoreDb, RequestContext._SCHEMAS).findOne(schemaId);

        if (Objects.isNull(document)) {
            return null;
        }

        return SchemaLoader.load(new JSONObject(document.toMap()));
    }

    /**
     *
     * @return
     */
    public static CacheSingleton getInstance() {
        return CachesSingletonHolder.INSTANCE;
    }

    private static class CachesSingletonHolder {
        private static final CacheSingleton INSTANCE = new CacheSingleton();

        private CachesSingletonHolder() {
        }
    }
}
