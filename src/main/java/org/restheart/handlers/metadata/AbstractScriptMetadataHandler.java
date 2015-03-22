/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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

import com.google.common.net.HttpHeaders;
import io.undertow.attribute.ExchangeAttributes;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.Date;
import javax.script.Bindings;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import org.restheart.hal.metadata.InvalidMetadataException;
import org.slf4j.Logger;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public abstract class AbstractScriptMetadataHandler extends PipedHttpHandler {
    /**
     * Creates a new instance of RequestScriptMetadataHandler
     *
     * @param next
     */
    public AbstractScriptMetadataHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (canCollRepresentationTransformersAppy(context)) {
            try {
                enforceCollRepresentationTransformLogic(exchange, context);
            } catch (InvalidMetadataException | ScriptException e) {
                context.addWarning("error evaluating script metadata: " + e.getMessage());
            }
        }

        if (canDBRepresentationTransformersAppy(context)) {
            try {
                enforceDbRepresentationTransformLogic(exchange, context);
            } catch (InvalidMetadataException | ScriptException e) {
                context.addWarning("error evaluating script metadata: " + e.getMessage());
            }
        }

        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }
    }

    abstract boolean canCollRepresentationTransformersAppy(RequestContext context);

    abstract boolean canDBRepresentationTransformersAppy(RequestContext context);

    abstract void enforceDbRepresentationTransformLogic(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException, ScriptException;

    abstract void enforceCollRepresentationTransformLogic(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException, ScriptException;
}
