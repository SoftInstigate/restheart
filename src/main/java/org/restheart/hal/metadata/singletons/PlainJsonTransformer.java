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
import org.restheart.Bootstrapper;
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
        RequestContext.REPRESENTATION_FORMAT rf = context.getRepresentationFormat();

        // can be null if an error occurs before RequestContextInjectorHandler.handle()
        if (rf == null) {
            rf = Bootstrapper.getConfiguration().getDefaultRepresentationFormat();
        }

        if (contentToTransform == null
                || (rf
                != RequestContext.REPRESENTATION_FORMAT.PJ
                && rf
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

            // add _embedded data
            BsonArray _embedded = new BsonArray();

            addElements(_embedded, embedded, "rh:doc");
            addElements(_embedded, embedded, "rh:file");
            addElements(_embedded, embedded, "rh:bucket");
            addElements(_embedded, embedded, "rh:db");
            addElements(_embedded, embedded, "rh:coll");
            addElements(_embedded, embedded, "rh:index");

            if (!_embedded.isEmpty()) {
                responseContent.append("_embedded", _embedded);
            }

            // add _results (for bulk operations)
            BsonArray _results = new BsonArray();
            addElements(_results, embedded, "rh:result");

            if (!_results.isEmpty()) {
                responseContent.append("_results", _results);
            }

            // add _errors if any
            BsonArray _errors = new BsonArray();
            addElements(_errors, embedded, "rh:error");

            if (!_errors.isEmpty()) {
                responseContent.append("_errors", _errors);
            }
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
