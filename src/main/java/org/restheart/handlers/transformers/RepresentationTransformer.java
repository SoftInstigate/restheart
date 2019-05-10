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
package org.restheart.handlers.transformers;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.Bootstrapper;
import org.restheart.representation.Resource;
import org.restheart.handlers.RequestContext;
import org.restheart.plugins.Transformer;
import org.restheart.representation.Resource.REPRESENTATION_FORMAT;
import org.restheart.utils.HttpStatus;

/**
 *
 * internally contentToTransform is always in HAL format
 * this transforms it to the target representation format
 * 
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class RepresentationTransformer implements Transformer {
    @Override
    public void transform(
            HttpServerExchange exchange,
            RequestContext context,
            BsonValue contentToTransform,
            BsonValue args) {
        if (!context.isInError()
                && (context.isDocument()
                || context.isFile()
                || context.isIndex())
                || context.isTxns()) {
            return;
        }

        // can be null if an error occurs before RequestContextInjectorHandler.handle()
        REPRESENTATION_FORMAT rf = context.getRepresentationFormat() != null ?
                context.getRepresentationFormat()
                : Bootstrapper.getConfiguration().getDefaultRepresentationFormat();
                        
        final boolean isStandardRepresentation = 
                rf == REPRESENTATION_FORMAT.STANDARD ||
                rf == REPRESENTATION_FORMAT.S;

        if (contentToTransform == null
                || (!isStandardRepresentation &&
                rf != REPRESENTATION_FORMAT.SHAL &&
                rf != REPRESENTATION_FORMAT.PLAIN_JSON &&
                rf != REPRESENTATION_FORMAT.PJ)) {
            return;
        }

        context.setResponseContentType(Resource.JSON_MEDIA_TYPE);

        BsonDocument responseContent = new BsonDocument();

        transformError(contentToTransform, responseContent);

        if (context.isInError()) {
            contentToTransform.asDocument().keySet().stream()
                    .filter(
                            key -> !"_embedded".equals(key)
                            && !"_links".equals(key))
                    .forEach(key -> responseContent
                    .append(key, contentToTransform
                            .asDocument()
                            .get(key)));

            context.setResponseContent(responseContent);
        } else if (context.isGet()) {
            transformRead(context, contentToTransform, responseContent);

            // add resource props if np is not specified
            if (!context.isNoProps() && !isStandardRepresentation) {
                contentToTransform.asDocument().keySet().stream()
                        .filter(key -> !"_embedded".equals(key))
                        .forEach(key
                                -> responseContent
                                .append(key, contentToTransform.asDocument()
                                        .get(key)));

                context.setResponseContent(responseContent);
            } else {
                // np specified, just return _embedded
                if (responseContent.containsKey("_embedded")
                        && responseContent.get("_embedded").isArray()) {
                    if (isStandardRepresentation && (
                            context.isRoot() ||
                            context.isDb())) {
                        BsonArray aresp = new BsonArray();
                        
                        responseContent.get("_embedded").asArray().stream()
                                .filter(obj -> obj.isDocument())
                                .map(obj -> obj.asDocument())
                                .filter(doc -> doc.containsKey("_id"))
                                .map(doc -> doc.get("_id"))
                                .forEachOrdered(_id -> aresp.add(_id));
                        
                        context.setResponseContent(aresp);
                    } else {
                        context.setResponseContent(
                                responseContent.get("_embedded"));
                    }
                } else {
                    context.setResponseContent(null);
                }
            }
        } else {
            transformWrite(contentToTransform, responseContent);

            context.setResponseContent(responseContent);
        }
    }

    private void transformError(
            BsonValue contentToTransform,
            BsonDocument responseContent) {

        if (contentToTransform.isDocument()) {
            BsonValue _embedded = contentToTransform
                    .asDocument()
                    .get("_embedded");

            if (_embedded != null) {
                BsonDocument embedded = _embedded.asDocument();

                // add _warnings if any
                BsonArray _warnings = new BsonArray();
                addItems(_warnings, embedded, "rh:warnings");

                if (!_warnings.isEmpty()) {
                    responseContent.append("_warnings", _warnings);
                }

                // add _errors if any
                BsonArray _errors = new BsonArray();
                addItems(_errors, embedded, "rh:error");

                if (!_errors.isEmpty()) {
                    responseContent.append("_errors", _errors);
                }

                // add _results if any
                if (embedded.containsKey("rh:result")) {
                    BsonArray bulkResp = embedded.get("rh:result")
                            .asArray();

                    if (bulkResp.size() > 0) {
                        BsonValue el = bulkResp.get(0);

                        if (el.isDocument()) {
                            BsonDocument doc = el.asDocument();

                            doc
                                    .keySet()
                                    .stream()
                                    .forEach(key
                                            -> responseContent
                                            .append(key, doc.get(key)));
                        }
                    }
                }

                // add _exception if any
                BsonArray _exception = new BsonArray();
                addItems(_exception, embedded, "rh:exception");

                if (!_exception.isEmpty()) {
                    responseContent.append("_exceptions", _exception);
                }
            }
        }
    }

    private void transformRead(
            RequestContext context,
            BsonValue contentToTransform,
            BsonDocument responseContent) {

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
                addItems(__embedded, embedded, "rh:result");
                addItems(__embedded, embedded, "rh:schema");

                // add _items if not in error
                if (context.getResponseStatusCode()
                        == HttpStatus.SC_OK) {
                    responseContent.append("_embedded", __embedded);
                }
            } else if (context.getResponseStatusCode()
                    == HttpStatus.SC_OK) {
                responseContent.append("_embedded", new BsonArray());
            }
        }
    }

    private void transformWrite(
            BsonValue contentToTransform,
            final BsonDocument responseContent) {

        if (contentToTransform.isDocument()) {
            if (contentToTransform.asDocument().containsKey("_embedded")
                    && contentToTransform.asDocument().get("_embedded")
                            .isDocument()
                    && contentToTransform.asDocument().get("_embedded")
                            .asDocument().containsKey("rh:result")
                    && contentToTransform.asDocument().get("_embedded")
                            .asDocument().get("rh:result").isArray()) {
                BsonArray bulkResp = contentToTransform.asDocument()
                        .get("_embedded").asDocument().get("rh:result")
                        .asArray();

                if (bulkResp.size() > 0) {
                    BsonValue el = bulkResp.get(0);

                    if (el.isDocument()) {
                        BsonDocument doc = el.asDocument();

                        doc
                                .keySet()
                                .stream()
                                .forEach(key
                                        -> responseContent
                                        .append(key, doc.get(key)));
                    }

                }
            }
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
