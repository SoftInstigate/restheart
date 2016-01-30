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

import java.util.Objects;
import org.bson.types.ObjectId;

/**
 *
 * if the id of the schema is a valid SchemaStoreURLm the schema can be loaded
 * from the restheart schema store (querying mongodb with caching and avoiding
 * the http overhead)
 *
 * the format is:
 *
 * http://schema-store/schemaStoreDb/schemaId
 *
 * schemaId refers to the mongodb _id and not to the id property
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SchemaStoreURL {
    private final String schemaDb;
    private final Object schemaId;

    public static final String SCHEMA_STORE_URL_PREFIX
            = "http://schema-store/";

    public SchemaStoreURL(String schemaDb, Object schemaId) {
        Objects.requireNonNull(schemaDb);
        Objects.requireNonNull(schemaId);

        if (schemaId instanceof String
                || schemaId instanceof ObjectId) {
            this.schemaDb = schemaDb;
            this.schemaId = schemaId;
        } else {
            throw new IllegalArgumentException(
                    "schemaId must be a String or an ObjectId");
        }
    }

    public SchemaStoreURL(String url) {
        Objects.requireNonNull(url);

        if (!isValid(url)) {
            throw new IllegalArgumentException("invalid url " + url);
        }

        String[] tokens = url.substring(20).split("/");

        this.schemaDb = tokens[0];
        this.schemaId = tokens[1].endsWith("#")
                ? tokens[1].substring(0, tokens[1].length())
                : tokens[1];
    }

    public String getSchemaDb() {
        return schemaDb;
    }

    public Object getSchemaId() {
        return schemaId;
    }

    @Override
    public String toString() {
        return SCHEMA_STORE_URL_PREFIX
                .concat(schemaDb)
                .concat("/")
                .concat(schemaId.toString())
                .concat("#");
    }

    public static boolean isValid(String url) {
        return url != null
                && url.startsWith(SCHEMA_STORE_URL_PREFIX)
                && count(url, "/") == 4;
    }

    private static int count(String s, String c) {
        return s.length() - s.replace(c, "").length();
    }
}
