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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.internal.DefaultSchemaClient;
import org.restheart.handlers.schema.JsonSchemaCacheSingleton;
import org.restheart.handlers.schema.JsonSchemaNotFoundException;
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

    private static final SchemaClient wrapped = new DefaultSchemaClient();
    private final URL requestUrl;

    public SchemaStoreClient(String requestUrl) {
        Objects.requireNonNull(requestUrl);

        try {
            this.requestUrl = new URL(requestUrl);
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("invalid url argument: " + requestUrl);
        }
    }

    @Override
    public InputStream get(String url) {
        LOGGER.debug("url " + url);

        if (isLocalSchemaStore(url)) {
            try {
                DBObject s = JsonSchemaCacheSingleton.getInstance().getRaw(getSchemaStoreDB(url), getSchemaId(url));
                return new ByteArrayInputStream(s.toString().getBytes());
            } catch (JsonSchemaNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            return wrapped.get(url);
        }
    }

    @Override
    public InputStream apply(String url) {
        return get(url);
    }

    /**
     *
     * @param url
     * @return true if the given url is points to a schema store of this server
     */
    private boolean isLocalSchemaStore(String url) {
        LOGGER.debug("*** {} {}", requestUrl, url);

        URL _url;

        try {
            _url = new URL(url);
        } catch (MalformedURLException ex) {
            return false;
        }

        return requestUrl.getHost().equals(_url.getHost())
                && requestUrl.getPort() == _url.getPort();
    }

    /**
     * warning: this is not working for urls rewritten by mongo-mounts conf
     * option
     *
     * @param url
     * @return the schema store db
     */
    private String getSchemaStoreDB(String url) {
        String[] tokens = url.split("/");

        return tokens.length >= 3 ? tokens[tokens.length - 3] : null;
    }

    /**
     * warning: this is not working for urls rewritten by mongo-mounts conf
     * option
     *
     * @param url
     * @return the schema id
     */
    private String getSchemaId(String url) {
        String[] tokens = url.split("/");
        return tokens.length >= 1 ? tokens[tokens.length - 1] : null;
    }
}
