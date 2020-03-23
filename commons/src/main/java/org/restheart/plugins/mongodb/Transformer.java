/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
