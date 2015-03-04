/*
 * RESTHeart - the data REST API server
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
import java.util.List;
import javax.script.Bindings;
import javax.script.ScriptException;
import org.restheart.hal.metadata.InvalidMetadataException;
import org.restheart.hal.metadata.RepresentationTransformer;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ResponseScriptMetadataHandler extends PipedHttpHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseScriptMetadataHandler.class);
   /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (doesRepresentationTransformLogicAppy(context)) {
            try {
                enforceRepresentationTransformLogic(exchange, context);
            } catch(InvalidMetadataException | ScriptException e) {
                context.addWarning("error evaluating response script metadata: " + e.getMessage());
            }
        }

        if (getNext() != null)
            getNext().handleRequest(exchange, context);
    }
    
    private boolean doesRepresentationTransformLogicAppy(RequestContext context) {
        return ((context.getType() == RequestContext.TYPE.DOCUMENT && context.getMethod() == RequestContext.METHOD.GET) || 
                (context.getType() == RequestContext.TYPE.COLLECTION && context.getMethod() == RequestContext.METHOD.GET))
                && context.getCollectionProps().containsField(RepresentationTransformer.RTLS_ELEMENT_NAME);
    }
    
    private void enforceRepresentationTransformLogic(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException, ScriptException {
        List<RepresentationTransformer> rts = RepresentationTransformer.getFromJson(context.getCollectionProps(), false);

            for (RepresentationTransformer rt : rts) {
                if (rt.getPhase() == RepresentationTransformer.PHASE.RESPONSE) {
                    rt.evaluate(RequestScriptMetadataHandler.getBindings(exchange, context, LOGGER));
                }
            }
    } 
}
