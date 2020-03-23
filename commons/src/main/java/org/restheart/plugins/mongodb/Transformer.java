/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
package org.restheart.plugins.mongodb;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonValue;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.plugins.Plugin;

/**
 * A Transformer applies a transformation on requests. It can apply both to
 * incoming requests data (body, qparams, etc) and response body for read
 * requests), depending on the RequestTransformer phase attribute.
 *
 * @see org.restheart.hal.metadata.RequestTransformer
 * @deprecated use org.restheart.plugins.Interceptor instead
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@Deprecated
public interface Transformer extends Plugin {
    /**
     *
     */
    public enum PHASE {

        /**
         *
         */
        REQUEST, 

        /**
         *
         */
        RESPONSE
    }

    /**
     *
     */
    public enum SCOPE {

        /**
         *
         */
        THIS, 

        /**
         *
         */
        CHILDREN
    }
    
    /**
     * contentToTransform can be directly manipulated or
     * RequestContext.setResponseContent(BsonValue value) for response phase and
     * RequestContext.setContent(BsonValue value) for request phase can be used
     *
     * @param exchange the server exchange
     * @param context the request context
     * @param contentToTransform the content data to transform
     * @param args the args sepcified in the collection metadata via args
     * property property
     */
    void transform(
            final HttpServerExchange exchange,
            final RequestContext context,
            BsonValue contentToTransform,
            final BsonValue args);

    /**
     *
     * @param exchange the server exchange
     * @param context the request context
     * @param contentToTransform the content data to transform
     * @param args the args sepcified in the collection metadata via args
     * property
     * @param confArgs the args specified in the configuration file via args
     * property
     */
    default void transform(
            HttpServerExchange exchange,
            RequestContext context,
            BsonValue contentToTransform,
            final BsonValue args,
            BsonValue confArgs) {
        transform(exchange, context, contentToTransform, args);
    }
}
