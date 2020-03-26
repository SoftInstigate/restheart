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

/**
 *
 * wraps a checker with args and confArgs to be added as a global checker
 *
 * @deprecated use org.restheart.plugins.Interceptor with
 * interceptPoint=RESPONSE_ASYNC instead
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@Deprecated
public class GlobalHook {
    private final Hook hook;
    private final RequestContextPredicate predicate;
    private final BsonValue args;
    private final BsonValue confArgs;

    /**
     *
     * @param hook
     * @param predicate hook is applied only to requests that resolve the
     * predicate
     * @param args
     * @param confArgs
     */
    public GlobalHook(Hook hook,
            RequestContextPredicate predicate,
            BsonValue args,
            BsonValue confArgs) {
        this.hook = hook;
        this.predicate = predicate;
        this.args = args;
        this.confArgs = confArgs;
    }

    /**
     *
     * @param exchange
     * @param context
     * @return
     */
    public boolean hook(
            HttpServerExchange exchange,
            RequestContext context) {

        return resolve(exchange, context)
                && this.getHook().hook(exchange,
                        context,
                        this.getArgs(),
                        this.getConfArgs());
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
     * @return the checker
     */
    public Hook getHook() {
        return hook;
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
