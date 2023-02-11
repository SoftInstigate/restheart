/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
package org.restheart.mongodb.services;

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.mongodb.interceptors.MetadataCachesSingleton;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "cacheInvalidator", description = "Invalidates the MongoService db and collection metadata cache", defaultURI = "/ic")
public class CacheInvalidator implements ByteArrayService {
    private static MetadataCachesSingleton CACHE = MetadataCachesSingleton.getInstance();

    /**
     *
     * @throws Exception
     */
    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        if (!MongoServiceConfiguration.get().isLocalCacheEnabled()) {
            response.setInError(HttpStatus.SC_NOT_MODIFIED, "caching is off");
            return;
        }

        if (request.isOptions()) {
            handleOptions(request);
            return;
        }

        if (!request.isPost()) {
            response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        var db = request.getQueryParameterOfDefault("db", null);
        var coll = request.getQueryParameterOfDefault("coll", null);

        if (db == null) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "the db query paramter is mandatory");
        } else if (coll == null) {
            CACHE.invalidateDb(db);
        } else {
            CACHE.invalidateCollection(db, coll);
        }

        response.setStatusCode(HttpStatus.SC_OK);
    }
}
