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
import org.restheart.hal.metadata.singletons.Transformer;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * this transformer is responsible of escaping in the aggregation stages that
 * can contain dollar prefixed keys (for operator) and . (dot notation)
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
            if (context.getMethod() == RequestContext.METHOD.PUT
                    || context.getMethod() == RequestContext.METHOD.PATCH) {
                contentToTransform.putAll(
                        (DBObject) JsonUtils.escapeKeys(contentToTransform, true));
            } else if (context.getMethod() == RequestContext.METHOD.GET) {
                contentToTransform.putAll(
                        (DBObject) JsonUtils.unescapeKeys(contentToTransform));
            }
        } else if ((context.getType() == RequestContext.TYPE.COLLECTION)
                && context.getMethod() == RequestContext.METHOD.GET) {
            // apply transformation on embedded schemas

            BasicDBObject _embedded = (BasicDBObject) contentToTransform.get("_embedded");

            contentToTransform.put("_embedded",
                    (DBObject) JsonUtils.unescapeKeys(_embedded));

        }
    }
}
