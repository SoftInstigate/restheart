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
package org.restheart.handlers.metadata;

import io.undertow.server.HttpServerExchange;
import java.util.Arrays;
import java.util.List;
import org.bson.BsonValue;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.metadata.TransformerMetadata;
import org.restheart.plugins.Transformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * handler that applies the transformers passed to the costructor
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class TransformersListHandler extends PipedHttpHandler {

    static final Logger LOGGER
            = LoggerFactory.getLogger(TransformersListHandler.class);

    private final List<Transformer> transformers;
    private final TransformerMetadata.PHASE phase;

    /**
     * Creates a new instance of TransformerHandler
     *
     * @param next
     * @param phase
     * @param transformers
     */
    public TransformersListHandler(
            PipedHttpHandler next,
            TransformerMetadata.PHASE phase,
            Transformer... transformers) {
        super(next);

        this.phase = phase;
        this.transformers = Arrays.asList(transformers);
    }

    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        
        
        if (doesTransformerAppy()) {
            transform(exchange, context);
        }

        next(exchange, context);
    }

    private boolean doesTransformerAppy() {
        return transformers != null
                && !transformers.isEmpty();
    }

    private void transform(
            HttpServerExchange exchange,
            RequestContext context)
            throws InvalidMetadataException {
        BsonValue data;

        if (this.phase == TransformerMetadata.PHASE.REQUEST) {
            data = context.getContent();
        } else {
            data = context.getResponseContent();
        }

        transformers.stream().forEachOrdered(
                t -> t.transform(exchange, context, data, null));
    }
}
