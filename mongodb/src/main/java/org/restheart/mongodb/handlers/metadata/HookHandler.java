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
package org.restheart.mongodb.handlers.metadata;

import io.undertow.server.HttpServerExchange;
import java.util.List;
import java.util.NoSuchElementException;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.mongodb.metadata.HookMetadata;
import org.restheart.plugins.GlobalHook;
import org.restheart.mongodb.plugins.MongoServicePluginsRegistry;
import org.restheart.mongodb.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * handler that executes the hooks defined in the collection properties
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class HookHandler extends PipelinedHandler {

    static final Logger LOGGER
            = LoggerFactory.getLogger(HookHandler.class);
    
    /**
     * Creates a new instance of HookMetadataHandler
     */
    public HookHandler() {
        super(null);
    }

    /**
     * Creates a new instance of HookMetadataHandler
     *
     * @param next
     */
    public HookHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
        var context = RequestContext.wrap(exchange);
        
        // execute global hooks
        executeGlobalHooks(exchange);
        
        if (request.getCollectionProps() != null
                && request.getCollectionProps()
                        .containsKey(HookMetadata.ROOT_KEY)) {

            List<HookMetadata> mdHooks = null;

            try {
                mdHooks = HookMetadata.getFromJson(
                        request.getCollectionProps());
            } catch (InvalidMetadataException ime) {
                response.addWarning(ime.getMessage());
            }

            if (mdHooks != null) {
                for (HookMetadata mdHook : mdHooks) {
                    try {
                        var hookRecord = MongoServicePluginsRegistry.getInstance()
                                .getHook(mdHook.getName());
                        var hook = hookRecord.getInstance();

                        var confArgs = JsonUtils.toBsonDocument(
                                hookRecord.getConfArgs());

                        if (hook.doesSupportRequests(context)) {
                            hook.hook(exchange,
                                    context,
                                    mdHook.getArgs(),
                                    confArgs);
                        }
                    } catch (NoSuchElementException ex) {
                        LOGGER.warn(ex.getMessage());
                    } catch (Throwable t) {
                        String err = "Error executing hook '"
                                + mdHook.getName()
                                + "': "
                                + t.getMessage();

                        LOGGER.warn(err);
                        response.addWarning(err);
                    }
                }
            }
        }

        next(exchange);
    }
    
    private void executeGlobalHooks(HttpServerExchange exchange) {
        var context = RequestContext.wrap(exchange);
        
        // execute global request tranformers
        getGlobalHooks().stream()
                .forEachOrdered(gh -> gh.hook(exchange, context));
    }
    
    /**
     * @deprecated use PluginsRegistry.getInstance().getGlobalHooks() instead
     * @return the GLOBAL_HOOKS
     */
    @Deprecated
    public static synchronized List<GlobalHook> getGlobalHooks() {
        return MongoServicePluginsRegistry.getInstance().getGlobalHooks();
    }
}
