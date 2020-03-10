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
package org.restheart.plugins;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonValue;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.handlers.exchange.RequestContextPredicate;

/**
 *
 * wraps a checker with args and confArgs to be added as a global checker
 * @deprecated use org.restheart.plugins.Interceptor instead
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
     * @param predicate hook is applied only to requests that resolve
     * the predicate
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
