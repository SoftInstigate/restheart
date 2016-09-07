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
import org.bson.BsonValue;
import org.restheart.handlers.RequestContext;

/**
 * A Transformer applies a transformation on request content. This can appen
 * both to incoming data (write requests) and to response data (read requests),
 * depending on the RepresentationTransformer phase attribute.
 *
 * @see org.restheart.hal.metadata.RepresentationTransformer
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Transformer {
    /**
     * contentToTransform can be directly manipulated or
     * RequestContext.setResponseContent(BsonValue value) for response phase and
     * RequestContext.setContent(BsonValue value) for request phase can be used
     *
     * @param exchange the server exchange
     * @param context the request context
     * @param contentToTransform the content data to transform
     * @param args the args sepcified in the collection metadata via args
     * property
     */
    void transform(
            final HttpServerExchange exchange,
            final RequestContext context,
            BsonValue contentToTransform,
            final BsonValue args);
}
