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
        if (contentToTransform == null 
                || (context.getRepresentationFormat()
                != RequestContext.REPRESENTATION_FORMAT.PJ
                && context.getRepresentationFormat()
                != RequestContext.REPRESENTATION_FORMAT.PLAIN_JSON)) {
            return;
        }

        BsonDocument responseContent = new BsonDocument();

        context.setResponseContentType(Representation.JSON_MEDIA_TYPE);

        if (!context.isInError() && contentToTransform.isDocument()
                && contentToTransform.asDocument().containsKey("_embedded")) {
            BsonDocument embedded = contentToTransform
                    .asDocument()
                    .get("_embedded")
                    .asDocument();

            BsonArray _embedded = new BsonArray();

            addElements(_embedded, embedded, "rh:doc");
            addElements(_embedded, embedded, "rh:file");
            addElements(_embedded, embedded, "rh:bucket");
            addElements(_embedded, embedded, "rh:db");
            addElements(_embedded, embedded, "rh:coll");
            addElements(_embedded, embedded, "rh:index");

            responseContent.append("_embedded", _embedded);
        } 

        if (!context.isNoProps()) {
            contentToTransform.asDocument().keySet().stream()
                    .filter(key -> !"_embedded".equals(key)
                    && !"_links".equals(key))
                    .forEach(key -> responseContent
                    .append(key, contentToTransform.asDocument()
                            .get(key)));
        }

        context.setResponseContent(responseContent);
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
