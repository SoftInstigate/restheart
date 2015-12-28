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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.internal.DefaultSchemaClient;
import org.restheart.handlers.schema.JsonSchemaCacheSingleton;
import org.restheart.handlers.schema.JsonSchemaNotFoundException;
import org.restheart.handlers.schema.SchemaStoreURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A json-schema client that loads the schema from the db if the url refer to a
 * schema store. If not it delegates to the DefaultSchemaClient.
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SchemaStoreClient implements SchemaClient {
    static final Logger LOGGER = LoggerFactory.getLogger(SchemaStoreClient.class);

    private static final SchemaClient WRAPPED = new DefaultSchemaClient();

    public SchemaStoreClient() {
    }

    @Override
    public InputStream get(String uri) {
        if (isLocalSchemaStore(uri)) {
            try {
                SchemaStoreURI _uri = new SchemaStoreURI(uri);

                DBObject s = JsonSchemaCacheSingleton.getInstance()
                        .getRaw(_uri.getSchemaDb(), _uri.getSchemaId());

                return new ByteArrayInputStream(s.toString().getBytes());
            } catch (JsonSchemaNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            return WRAPPED.get(uri);
        }
    }

    @Override
    public InputStream apply(String url) {
        return get(url);
    }

    /**
     *
     * @param id
     * @return true if the given id is a schema store of this server
     */
    private boolean isLocalSchemaStore(String id) {
        return SchemaStoreURI.isValid(id);
    }
}
