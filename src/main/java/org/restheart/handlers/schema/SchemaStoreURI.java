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
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SchemaStoreURI {
    private final String schemaDb;
    private final Object schemaId;

    public SchemaStoreURI(String schemaDb, Object schemaId) {
        Objects.requireNonNull(schemaDb);
        Objects.requireNonNull(schemaId);

        if (schemaId instanceof String
                || schemaId instanceof ObjectId) {
            this.schemaDb = schemaDb;
            this.schemaId = schemaId;
        } else {
            throw new IllegalArgumentException("schemaId must be a String or an ObjectId");
        }
    }

    public SchemaStoreURI(String uri) {
        Objects.requireNonNull(uri);

        if (!isValid(uri)) {
            throw new IllegalArgumentException("invalid uri " + uri);
        }

        String[] tokens = uri.substring(9).split("/");

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

        return "schema://"
                .concat(schemaDb)
                .concat("/")
                .concat(schemaId.toString())
                .concat("#");
    }

    public static boolean isValid(String uri) {
        return uri != null
                && uri.startsWith("schema://")
                && count(uri, "/") == 3;
    }

    private static int count(String s, String c) {
        return s.length() - s.replace(c, "").length();
    }
}
