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

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import java.util.Optional;
import org.restheart.hal.metadata.AbstractAggregationOperation;
import org.restheart.hal.metadata.singletons.Transformer;
import org.restheart.handlers.RequestContext;
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
    public void tranform(HttpServerExchange exchange,
            RequestContext context,
            final DBObject contentToTransform, DBObject args) {

        if (contentToTransform == null) {
            // nothing to do
            return;
        }

        if (context.getType() == RequestContext.TYPE.COLLECTION) {
            BasicDBList aggrs = getAggregationMetadata(contentToTransform);

            if (aggrs == null) {
                // nothing to do
                return;
            }

            if (context.getMethod() == RequestContext.METHOD.PUT
                    || context.getMethod() == RequestContext.METHOD.PATCH) {
                contentToTransform.put(AbstractAggregationOperation.AGGREGATIONS_ELEMENT_NAME,
                        (DBObject) JsonUtils.escapeKeys(aggrs, true));
            } else if (context.getMethod() == RequestContext.METHOD.GET) {
                contentToTransform.put(AbstractAggregationOperation.AGGREGATIONS_ELEMENT_NAME,
                        (DBObject) JsonUtils.unescapeKeys(aggrs));
            }
        } else if ((context.getType() == RequestContext.TYPE.DB)
                && context.getMethod() == RequestContext.METHOD.GET) {
            // apply transformation on embedded schemas

            BasicDBObject _embedded = (BasicDBObject) contentToTransform.get("_embedded");

            if (_embedded == null) {
                // nothing to do
                return;
            }

            BasicDBList colls = (BasicDBList) _embedded.get("rh:coll");

            if (colls == null) {
                // nothing to do
                return;
            }

            colls.stream()
                    .filter(coll -> {
                        return coll instanceof BasicDBObject;
                    })
                    .forEach(_coll -> {
                        BasicDBObject coll = (BasicDBObject) _coll;

                        BasicDBList aggrs = getAggregationMetadata(coll);

                        if (aggrs == null) {
                            // nothing to do
                            return;
                        }

                        coll.put(AbstractAggregationOperation.AGGREGATIONS_ELEMENT_NAME,
                                (DBObject) JsonUtils.unescapeKeys(aggrs));
                    });
        }
    }

    private BasicDBList getAggregationMetadata(DBObject contentToTransform) {
        List<Optional<Object>> ___aggrs = JsonUtils
                .getPropsFromPath(contentToTransform,
                        "$." + AbstractAggregationOperation.AGGREGATIONS_ELEMENT_NAME);

        if (___aggrs == null || ___aggrs.isEmpty()) {
            return null;
        }

        Optional<Object> __aggrs = ___aggrs.get(0);

        if (__aggrs == null || !__aggrs.isPresent()) {
            return null;
        }

        Object _aggrs = __aggrs.get();

        if (_aggrs instanceof BasicDBList) {
            return (BasicDBList) _aggrs;
        } else {
            return null;
        }
    }
}
