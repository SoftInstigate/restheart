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
package org.restheart.plugins;

import org.restheart.utils.URLUtils;

/**
 * simple class to hold a mongo-mount configuration entry
 * 
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class MongoMount {
    public String resource;
    public String uri;

    public MongoMount(String resource, String uri) {
        if (uri == null) {
            throw new IllegalArgumentException("'where' cannot be null. check your 'mongo-mounts'.");
        }

        if (!uri.startsWith("/")) {
            throw new IllegalArgumentException("'where' must start with \"/\". check your 'mongo-mounts'");
        }

        if (resource == null) {
            throw new IllegalArgumentException("'what' cannot be null. check your 'mongo-mounts'.");
        }

        if (!uri.startsWith("/") && !uri.equals("*")) {
            throw new IllegalArgumentException("'what' must be * (all db resorces) or start with \"/\". (eg. /db/coll) check your 'mongo-mounts'");
        }

        this.resource = resource;
        this.uri = URLUtils.removeTrailingSlashes(uri);
    }

    @Override
    public String toString() {
        return "MongoMount(" + uri + " -> " + resource + ")";
    }
}
