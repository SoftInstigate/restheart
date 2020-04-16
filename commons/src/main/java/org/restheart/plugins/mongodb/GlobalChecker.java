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
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.RequestContext;
import org.restheart.exchange.RequestContextPredicate;

/**
 *
 * wraps a checker with args and confArgs to be added as a global checker
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class GlobalChecker {
    private final Checker checker;
    private final RequestContextPredicate predicate;
    private final boolean skipNotSupported;
    private final BsonValue args;
    private final BsonValue confArgs;

    /**
     * 
     * @param checker
     * @param predicate checker is applied only to requests that resolve
     * the predicate
     * @param skipNotSupported
     * @param args
     * @param confArgs 
     */
    public GlobalChecker(Checker checker,
            RequestContextPredicate predicate,
            boolean skipNotSupported,
            BsonValue args,
            BsonValue confArgs) {
        this.checker = checker;
        this.predicate = predicate;
        this.skipNotSupported = skipNotSupported;
        this.args = args;
        this.confArgs = confArgs;
    }

    /**
     *
     * @param exchange
     * @param context
     * @param contentToCheck
     * @return
     */
    public boolean check(
            HttpServerExchange exchange,
            RequestContext context,
            BsonDocument contentToCheck) {

        return resolve(exchange, context)
                && this.getChecker().check(exchange,
                        context,
                        contentToCheck, 
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
     *
     * @param context
     * @return
     */
    public Checker.PHASE getPhase(RequestContext context) {
        return this.getChecker().getPhase(context);
    }

    /**
     *
     * @param context
     * @return
     */
    public boolean doesSupportRequests(RequestContext context) {
        return this.getChecker().doesSupportRequests(context);
    }

    /**
     * @return the checker
     */
    public Checker getChecker() {
        return checker;
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
     * @return the skipNotSupported
     */
    public boolean isSkipNotSupported() {
        return skipNotSupported;
    }

    /**
     * @return the predicate
     */
    public RequestContextPredicate getPredicate() {
        return predicate;
    }
}
