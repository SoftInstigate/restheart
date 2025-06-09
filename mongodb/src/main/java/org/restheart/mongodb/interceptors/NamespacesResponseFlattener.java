/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
package org.restheart.mongodb.interceptors;

import org.bson.BsonArray;
import org.restheart.exchange.ExchangeKeys.REPRESENTATION_FORMAT;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;

/**
 * The response of GET / and GET /db is an array of documents
 *
 * This flattens the reponse in an array of strings (names of dbs and of
 * collections respectiverly)
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 */
@RegisterPlugin(name = "namespacesResponseFlattener",
        description = "flattens the response of GET / and GET /db to a simple array of names for STANDARD representation format",
        interceptPoint = InterceptPoint.RESPONSE)
public class NamespacesResponseFlattener implements MongoInterceptor {
    /**
     *
     */
    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var content = response.getContent().asArray();

        var flatContent = new BsonArray();

        content.stream()
                .filter(doc -> doc.isDocument())
                .map(doc -> doc.asDocument())
                .map(doc -> doc.get("_id"))
                .forEachOrdered(flatContent::add);

        response.setContent(flatContent);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
                && (request.getRepresentationFormat()== REPRESENTATION_FORMAT.STANDARD
                || request.getRepresentationFormat()== REPRESENTATION_FORMAT.S)
                && request.isGet()
                && (request.isRoot() || request.isDb())
                && response.getContent() != null
                && response.getContent().isArray()
                && !response.getContent().asArray().isEmpty();
    }
}
