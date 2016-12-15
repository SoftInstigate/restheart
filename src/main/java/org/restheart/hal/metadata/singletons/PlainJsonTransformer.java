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
import org.restheart.handlers.RequestContext.METHOD;
import org.restheart.handlers.RequestContext.TYPE;
import org.restheart.utils.HttpStatus;

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
        if (!context.isInError()
                && (context.getType() == TYPE.DOCUMENT
                || context.getType() == TYPE.FILE
                || context.getType() == TYPE.INDEX)) {
            return;
        }

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

        if (contentToTransform.isDocument()) {
            BsonValue _embedded = contentToTransform
                    .asDocument()
                    .get("_embedded");

            if (_embedded != null) {
                BsonDocument embedded = _embedded.asDocument();

                // add _items data
                BsonArray __embedded = new BsonArray();

                addItems(__embedded, embedded, "rh:doc");
                addItems(__embedded, embedded, "rh:file");
                addItems(__embedded, embedded, "rh:bucket");
                addItems(__embedded, embedded, "rh:db");
                addItems(__embedded, embedded, "rh:coll");
                addItems(__embedded, embedded, "rh:index");

                // add _items if not in error
                if (context.getMethod() == METHOD.GET
                        && context.getResponseStatusCode()
                        == HttpStatus.SC_OK) {
                    responseContent.append("_embedded", __embedded);
                }

                // add _results (for bulk operations)
                BsonArray _results = new BsonArray();
                addItems(_results, embedded, "rh:result");

                if (!_results.isEmpty()) {
                    responseContent.append("_results", _results);
                }

                // add _errors if any
                BsonArray _errors = new BsonArray();
                addItems(_errors, embedded, "rh:error");

                if (!_errors.isEmpty()) {
                    responseContent.append("_errors", _errors);
                }

                // add _exception if any
                BsonArray _exception = new BsonArray();
                addItems(_exception, embedded, "rh:exception");

                if (!_exception.isEmpty()) {
                    responseContent.append("_exceptions", _exception);
                }

                // add _warnings if any
                BsonArray _warnings = new BsonArray();
                addItems(_warnings, embedded, "rh:warnings");

                if (!_warnings.isEmpty()) {
                    responseContent.append("_warnings", _warnings);
                }
            } else if (context.getMethod() == METHOD.GET
                    && context.getResponseStatusCode()
                    == HttpStatus.SC_OK) {
                responseContent.append("_embedded", new BsonArray());
            }
        }

        if (!context.isNoProps() || context.isInError()) {
            contentToTransform.asDocument().keySet().stream()
                    .filter(key -> !"_embedded".equals(key)
                    && !"_links".equals(key))
                    .forEach(key -> responseContent
                    .append(key, contentToTransform.asDocument()
                            .get(key)));

            context.setResponseContent(responseContent);
        } else if (!context.isInError()) {
            // np specified, just return the most appropriate array
            if (responseContent.get("_errors") != null
                    && !responseContent.get("_errors").asArray().isEmpty()) {
                context.setResponseContent(responseContent.get("_errors"));
            } else if (responseContent.get("_results") != null
                    && !responseContent.get("_results").asArray().isEmpty()) {
                context.setResponseContent(responseContent.get("_results"));
            } else if (responseContent.get("_embedded") != null) {
                context.setResponseContent(responseContent.get("_embedded"));
            } else if (responseContent.get("_exception") != null
                    && !responseContent.get("_exception").asArray().isEmpty()) {
                context.setResponseContent(responseContent.get("_exception"));
            } else {
                context.setResponseContent(null);
            }
        } else {
            context.setResponseContent(responseContent);
        }
    }

    private void addItems(BsonArray elements, BsonDocument items, String ns) {
        if (items.containsKey(ns)) {
            elements.addAll(
                    items
                            .get(ns)
                            .asArray());
        }
    }
}
