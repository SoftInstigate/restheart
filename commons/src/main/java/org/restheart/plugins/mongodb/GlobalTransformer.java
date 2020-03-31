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
import org.restheart.handlers.exchange.RequestContextPredicate;
import org.restheart.plugins.mongodb.Transformer.PHASE;
import org.restheart.plugins.mongodb.Transformer.SCOPE;

/**
 * wraps a transformer with args and confArgs to be added as a global
 * transformer
 * 
 * @deprecated use org.restheart.plugins.Interceptor instead
 * 
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@Deprecated
public class GlobalTransformer {
    private final Transformer transformer;
    private final RequestContextPredicate predicate;
    private final PHASE phase;
    private final SCOPE scope;
    private final BsonValue args;
    private final BsonValue confArgs;

    /**
     *
     * @param transformer
     * @param phase
     * @param scope
     * @param predicate the transformer is applied only to requests that resolve
     * the predicate
     * @param args
     * @param confArgs
     */
    public GlobalTransformer(Transformer transformer,
            RequestContextPredicate predicate,
            PHASE phase,
            SCOPE scope,
            BsonValue args,
            BsonValue confArgs) {
        this.transformer = transformer;
        this.predicate = predicate;
        this.phase = phase;
        this.scope = scope;
        this.args = args;
        this.confArgs = confArgs;
    }

    /**
     *
     * @param exchange
     * @param context
     * @param contentToTransform
     */
    public void transform(
            HttpServerExchange exchange,
            RequestContext context,
            BsonValue contentToTransform) {
        if (resolve(exchange, context)) {
            this.getTransformer().
                    transform(exchange,
                            context,
                            contentToTransform, this.getArgs(), this.getConfArgs());
        }
    }

    /**
     *
     * @param exchange
     * @param context
     * @return
     */
    public boolean resolve(HttpServerExchange exchange,
            RequestContext context) {
        return this.predicate.resolve(exchange, context);
    }

    /**
     * @return the phase
     */
    public PHASE getPhase() {
        return phase;
    }

    /**
     * @return the scope
     */
    public SCOPE getScope() {
        return scope;
    }

    /**
     * @return the transformer
     */
    public Transformer getTransformer() {
        return transformer;
    }

    /**
     * @return the args
     */
    public BsonValue getArgs() {
        return args;
    }

    /**
     * @return the confArgs
     */
    public BsonValue getConfArgs() {
        return confArgs;
    }

    /**
     * @return the predicate
     */
    public RequestContextPredicate getPredicate() {
        return predicate;
    }
}
