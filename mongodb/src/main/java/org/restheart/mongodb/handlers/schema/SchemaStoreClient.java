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
package org.restheart.mongodb.handlers.schema;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import org.bson.BsonDocument;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.internal.DefaultSchemaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A json-schema client that loads the schema from the db if the url refer to a
 * schema store. If not it delegates to the DefaultSchemaClient.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SchemaStoreClient implements SchemaClient {

    static final Logger LOGGER
            = LoggerFactory.getLogger(SchemaStoreClient.class);

    private static final SchemaClient WRAPPED = new DefaultSchemaClient();

    /**
     *
     */
    public SchemaStoreClient() {
    }

    /**
     *
     * @param uri
     * @return
     */
    @Override
    public InputStream get(String uri) {
        LOGGER.trace("@@@ loading schema {}", uri);

        if (isLocalSchemaStore(uri)) {
            try {
                SchemaStoreURL _uri = new SchemaStoreURL(uri);

                BsonDocument s = JsonSchemaCacheSingleton
                        .getInstance()
                        .getRaw(
                                _uri.getSchemaDb(),
                                _uri.getSchemaId());

                return new ByteArrayInputStream(s.toString().getBytes("UTF-8"));
            } catch (JsonSchemaNotFoundException | UnsupportedEncodingException ex) {
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
        return SchemaStoreURL.isValid(id);
    }
}
