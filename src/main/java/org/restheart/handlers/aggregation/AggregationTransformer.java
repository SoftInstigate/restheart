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
package org.restheart.handlers.aggregation;

import io.undertow.server.HttpServerExchange;
import java.util.List;
import java.util.Optional;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.RequestContext;
import org.restheart.plugins.Transformer;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * transformer in charge of escaping the aggregation stages that can contain
 * dollar prefixed keys (for operator) use the dot notation
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AggregationTransformer implements Transformer {
    static final Logger LOGGER = LoggerFactory
            .getLogger(AggregationTransformer.class);

    @Override
    public void transform(
            HttpServerExchange exchange,
            RequestContext context,
            final BsonValue contentToTransform,
            BsonValue args) {
        if (contentToTransform == null) {
            // nothing to do
            return;
        }

        if (!contentToTransform.isDocument()) {
            throw new IllegalStateException(
                    "content to transform is not a document");
        }

        BsonDocument _contentToTransform = contentToTransform.asDocument();

        if (context.getType() == RequestContext.TYPE.COLLECTION) {
            BsonArray aggrs = getAggregationMetadata(_contentToTransform);

            if (aggrs == null) {
                // nothing to do
                return;
            }

            if (context.getMethod() == RequestContext.METHOD.PUT
                    || context.getMethod() == RequestContext.METHOD.PATCH) {
                _contentToTransform.put(AbstractAggregationOperation.AGGREGATIONS_ELEMENT_NAME,
                        JsonUtils.escapeKeys(aggrs, true));
            } else if (context.getMethod() == RequestContext.METHOD.GET) {
                _contentToTransform.put(AbstractAggregationOperation.AGGREGATIONS_ELEMENT_NAME,
                        JsonUtils.unescapeKeys(aggrs));
            }
        } else if ((context.getType() == RequestContext.TYPE.DB)
                && context.getMethod() == RequestContext.METHOD.GET) {
            // apply transformation on embedded schemas

            if (_contentToTransform.containsKey("_embedded")) {
                BsonValue _embedded = _contentToTransform.get("_embedded");

                if (_embedded.isDocument()
                        && _embedded.asDocument().containsKey("rh:coll")
                        && _embedded.asDocument().get("rh:coll").isArray()) {

                    BsonArray colls = _embedded
                            .asDocument()
                            .get("rh:coll")
                            .asArray();

                    colls.stream()
                            .filter(coll -> {
                                return coll.isDocument();
                            })
                            .forEach(_coll -> {
                                BsonDocument coll = _coll.asDocument();

                                BsonArray aggrs = getAggregationMetadata(coll);

                                if (aggrs == null) {
                                    // nothing to do
                                    return;
                                }

                                coll.put(AbstractAggregationOperation.AGGREGATIONS_ELEMENT_NAME,
                                        JsonUtils.unescapeKeys(aggrs));
                            });
                }
            }
        }
    }

    private BsonArray getAggregationMetadata(BsonDocument contentToTransform) {
        List<Optional<BsonValue>> ___aggrs = JsonUtils
                .getPropsFromPath(contentToTransform,
                        "$." + AbstractAggregationOperation.AGGREGATIONS_ELEMENT_NAME);

        if (___aggrs == null || ___aggrs.isEmpty()) {
            return null;
        }

        Optional<BsonValue> __aggrs = ___aggrs.get(0);

        if (__aggrs == null || !__aggrs.isPresent()) {
            return null;
        }

        BsonValue _aggrs = __aggrs.get();

        if (_aggrs.isArray()) {
            return _aggrs.asArray();
        } else {
            return null;
        }
    }
}
