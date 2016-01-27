/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) 2014 - 2016 SoftInstigate Srl
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

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.restheart.hal.metadata.InvalidMetadataException;
import org.restheart.hal.metadata.singletons.Transformer;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * handler that applies the transformers passed to the costructor
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class TransformerHandler extends PipedHttpHandler {
    static final Logger LOGGER = LoggerFactory.getLogger(TransformerHandler.class);

    private final List<Transformer> transformers;

    /**
     * Creates a new instance of TransformerHandler
     *
     * @param next
     * @param transformers
     */
    public TransformerHandler(PipedHttpHandler next, Transformer... transformers) {
        super(next);

        this.transformers = Arrays.asList(transformers);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (doesTransformerAppy()) {
            transform(exchange, context);
        }

        getNext().handleRequest(exchange, context);
    }

    private boolean doesTransformerAppy() {
        return transformers != null
                && !transformers.isEmpty();
    }

    private void transform(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException {
        if (!(context.getContent() instanceof BasicDBObject)) {
            throw new RuntimeException("this hanlder only supports content of type json object; content " +
                    context.getContent());
        }

        transformers.stream().forEachOrdered(t -> t.tranform(exchange, context, context.getContent(), null));
    }
}