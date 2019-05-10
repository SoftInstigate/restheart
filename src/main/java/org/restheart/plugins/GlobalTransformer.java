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

import org.restheart.plugins.Transformer;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonValue;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContextPredicate;
import org.restheart.metadata.TransformerMetadata;

/**
 * wraps a transformer with args and confArgs to be added as a global
 * transformer
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class GlobalTransformer {
    private final Transformer transformer;
    private final RequestContextPredicate predicate;
    private final TransformerMetadata.PHASE phase;
    private final TransformerMetadata.SCOPE scope;
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
            TransformerMetadata.PHASE phase,
            TransformerMetadata.SCOPE scope,
            BsonValue args,
            BsonValue confArgs) {
        this.transformer = transformer;
        this.predicate = predicate;
        this.phase = phase;
        this.scope = scope;
        this.args = args;
        this.confArgs = confArgs;
    }

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

    public boolean resolve(HttpServerExchange exchange,
            RequestContext context) {
        return this.predicate.resolve(exchange, context);
    }

    /**
     * @return the phase
     */
    public TransformerMetadata.PHASE getPhase() {
        return phase;
    }

    /**
     * @return the scope
     */
    public TransformerMetadata.SCOPE getScope() {
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
