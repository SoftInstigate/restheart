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

import java.util.Objects;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.utils.BsonUtils;

/**
 *
 * if the id of the schema is a valid SchemaStoreURL it can be loaded from the
 * schema store (querying mongodb with caching and avoiding the http overhead)
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

    /**
     *
     */
    public static final String SCHEMA_STORE_URL_PREFIX
            = "http://schema-store/";

    /**
     *
     * @param url
     * @return
     */
    public static boolean isValid(String url) {
        return url != null
                && url.startsWith(SCHEMA_STORE_URL_PREFIX)
                && count(url, "/") == 4;
    }

    private static int count(String s, String c) {
        return s.length() - s.replace(c, "").length();
    }
    private final String schemaDb;
    private final BsonValue schemaId;

    /**
     *
     * @param schemaDb
     * @param schemaId
     */
    public SchemaStoreURL(String schemaDb, BsonValue schemaId) {
        Objects.requireNonNull(schemaDb);
        Objects.requireNonNull(schemaId);

        if (schemaId.isString()
                || schemaId.isObjectId()) {
            this.schemaDb = schemaDb;
            this.schemaId = schemaId;
        } else {
            throw new IllegalArgumentException(
                    "schemaId must be a String or an ObjectId");
        }
    }

    /**
     *
     * @param url
     */
    public SchemaStoreURL(String url) {
        Objects.requireNonNull(url);

        if (!isValid(url)) {
            throw new IllegalArgumentException("invalid url " + url);
        }

        String[] tokens = url.substring(20).split("/");

        this.schemaDb = tokens[0];
        this.schemaId = new BsonString(tokens[1].endsWith("#")
                ? tokens[1].substring(0, tokens[1].length())
                : tokens[1]);
    }

    /**
     *
     * @return
     */
    public String getSchemaDb() {
        return schemaDb;
    }

    /**
     *
     * @return
     */
    public BsonValue getSchemaId() {
        return schemaId;
    }

    @Override
    public String toString() {
        return SCHEMA_STORE_URL_PREFIX
                .concat(schemaDb)
                .concat("/")
                .concat(BsonUtils.getIdAsString(schemaId, false))
                .concat("#");
    }

}
