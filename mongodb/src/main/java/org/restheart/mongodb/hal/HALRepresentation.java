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
package org.restheart.mongodb.hal;

import java.nio.channels.IllegalSelectorException;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.MongoService;
import org.restheart.mongodb.db.BulkOperationResult;
import org.restheart.mongodb.utils.URLUtils;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.representation.IllegalQueryParamenterException;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transform the response content to HAL or SHAL format when required (e.g. when
 * the request has the query parameter ?rep=HAL)
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "halRepresentation",
        description = "transforms the response to the HAL format if requested",
        interceptPoint = InterceptPoint.RESPONSE,
        priority = Integer.MAX_VALUE)
public class HALRepresentation implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(MongoService.class);

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var content = response.getContent();

        BsonDocument hal;

        if (request.isGet() || request.isBulkDocuments()
                || (request.isCollection()
                && request.isPost() && request.getContent().isArray())) {
            try {
                hal = std2HAL(request, response, content);
            } catch (IllegalSelectorException ise) {
                LOGGER.warn("Cannot transform response to HAL", ise);
                return;
            }
        } else {
            return;
        }

        response.setContentType(Resource.HAL_JSON_MEDIA_TYPE);

        if (Resource.isSHAL(request)
                && !request.isDocument()
                && !request.isFile()
                && !request.isSchema()) {
            response.setContent(hal2SHAL(request, response, hal));
        } else {
            response.setContent(hal);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return !request.isInError()
                && !request.isDbMeta()
                && !request.isCollectionMeta()
                && !request.isCollectionSize()
                && !request.isDbSize()
                && request.isHandledBy("mongo")
                && request.getRepresentationFormat() != null
                && (Resource.isSHAL(request) || Resource.isHAL(request));
    }

    /**
     * transform the response content to HAL format
     *
     * @param request
     * @param response
     * @param content
     * @return
     */
    private BsonDocument std2HAL(MongoRequest request,
            MongoResponse response,
            BsonValue content) {

        if (request.isGet()
                && (request.isDocument()
                || request.isFile()
                || request.isSchema())) {
            var factory = new DocumentRepresentationFactory();

            try {
                return factory
                        .getRepresentation(
                                URLUtils.removeTrailingSlashes(request.getPath()),
                                request.getExchange(),
                                content.asDocument())
                        .asBsonDocument();
            } catch (IllegalQueryParamenterException iqpe) {
                //shoudn't happen
                throw new IllegalStateException(iqpe);
            }
        } else if (request.isBulkDocuments()
                || (request.isCollection()
                && request.getContent() != null
                && request.getContent().isArray()
                && response.getDbOperationResult() != null
                && response.getDbOperationResult() instanceof BulkOperationResult)) {
            var factory = new BulkResultRepresentationFactory();

            try {
                return factory.getRepresentation(request.getExchange(),
                        (BulkOperationResult) response.getDbOperationResult())
                        .asBsonDocument();
            } catch (IllegalQueryParamenterException iqpe) {
                // shoudn't happen
                throw new IllegalStateException(iqpe);
            }

        } else {
            AbstractRepresentationFactory factory = null;

            if (request.isGet() && request.isRoot()) {
                factory = new RootRepresentationFactory();
            } else if (request.isGet() && request.isDb()) {
                factory = new DBRepresentationFactory();
            } else if (request.isGet() && request.isCollectionIndexes()) {
                factory = new IndexesRepresentationFactory();
            } else if (request.isGet() && (request.isCollection()
                    || request.isFilesBucket()
                    || request.isSchemaStore())) {
                factory = new CollectionRepresentationFactory();
            } else if (request.isGet() && request.isAggregation()) {
                factory = new AggregationResultRepresentationFactory();
            } else {
                throw new IllegalStateException("Cannot transform response "
                        + "to HAL format, not managed request type");
            }

            try {
                return factory
                        .getRepresentation(request.getExchange(),
                                content == null ? null : content.asArray(),
                                response.getCount())
                        .asBsonDocument();
            } catch (IllegalQueryParamenterException iqpe) {
                // shoudn't happen
                throw new IllegalStateException(iqpe);
            }
        }
    }

    /**
     * transforms HAL format to SHAL format
     *
     * @param request
     * @param response
     * @param content
     * @return
     */
    private BsonValue hal2SHAL(MongoRequest request,
            MongoResponse response,
            BsonDocument content) {
        if (request.isInError()) {
            var ret = new BsonDocument();

            content.asDocument().keySet().stream()
                    .filter(key -> !"_embedded".equals(key)
                    && !"_links".equals(key))
                    .forEach(key -> ret.append(key, content.asDocument().get(key)));

            return ret;
        } else if (request.isGet()) {
            // transform hal->shal
            var shal = readHAL2SHAL(response, content);

            // add resource props if np is not specified
            if (!request.isNoProps()) {
                shal.asDocument().keySet().stream()
                        .filter(key -> !"_embedded".equals(key))
                        .forEach(key
                                -> shal
                                .append(key, shal.get(key)));

                return shal;
            } else {
                // np specified, just return _embedded
                if (shal.containsKey("_embedded")
                        && shal.get("_embedded").isArray()) {

                    return shal.get("_embedded");
                } else {
                    return null;
                }
            }
        } else {
            return writeHAL2SHAL(content);
        }
    }

    private BsonDocument readHAL2SHAL(MongoResponse response, BsonValue content) {
        var ret = new BsonDocument();

        if (content != null && content.isDocument()) {
            content.asDocument().keySet().stream()
                    .filter(key -> !"_embedded".equals(key)
                    && !"_links".equals(key))
                    .forEach(key -> ret.append(key, content.asDocument().get(key)));

            BsonValue _embedded = content.asDocument().get("_embedded");

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
                addItems(__embedded, embedded, "rh:schema-store");

                // add _items if not in error
                if (response.getStatusCode()
                        == HttpStatus.SC_OK) {
                    ret.append("_embedded", __embedded);
                }
            } else if (response.getStatusCode()
                    == HttpStatus.SC_OK) {
                ret.append("_embedded", new BsonArray());
            }
        }

        return ret;
    }

    private BsonDocument writeHAL2SHAL(BsonValue content) {
        var ret = new BsonDocument();

        if (content != null && content.isDocument()) {
            if (content.asDocument().containsKey("_embedded")
                    && content.asDocument().get("_embedded")
                            .isDocument()
                    && content.asDocument().get("_embedded")
                            .asDocument().containsKey("rh:result")
                    && content.asDocument().get("_embedded")
                            .asDocument().get("rh:result").isArray()) {
                BsonArray bulkResp = content.asDocument()
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
                                        -> ret
                                        .append(key, doc.get(key)));
                    }

                }
            }
        }

        return ret;
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
