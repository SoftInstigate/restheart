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

import io.undertow.server.HttpServerExchange;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.hal.Representation;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PlainJsonTransformer implements Transformer {
    @Override
    public void transform(
            HttpServerExchange exchange,
            RequestContext context,
            BsonValue contentToTransform,
            BsonValue args) {
        if (!exchange.getQueryParameters().containsKey("pj")) {
            return;
        }

        context.setResponseContentType(Representation.JSON_MEDIA_TYPE);

        if (contentToTransform == null) {
            context.setResponseContent(new BsonArray());
        } else if (contentToTransform.isDocument()
                && contentToTransform.asDocument().containsKey("_embedded")) {
            BsonDocument embedded = contentToTransform
                    .asDocument()
                    .get("_embedded")
                    .asDocument();

            BsonArray elements = new BsonArray();

            addElements(elements, embedded, "rh:doc");
            addElements(elements, embedded, "rh:file");
            addElements(elements, embedded, "rh:bucket");
            addElements(elements, embedded, "rh:db");
            addElements(elements, embedded, "rh:coll");
            addElements(elements, embedded, "rh:index");

            context.setResponseContent(elements);
        } else {
            context.setResponseContent(new BsonArray());
        }
    }

    private void addElements(BsonArray elements, BsonDocument embedded, String ns) {
        if (embedded.containsKey(ns)) {
            elements.addAll(
                    embedded
                    .get(ns)
                    .asArray());
        }
    }
}
