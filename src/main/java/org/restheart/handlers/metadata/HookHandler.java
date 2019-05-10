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
import java.util.List;
import java.util.NoSuchElementException;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.metadata.HookMetadata;
import org.restheart.plugins.PluginsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * handler that executes the hooks defined in the collection properties
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class HookHandler extends PipedHttpHandler {

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
    public HookHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        if (context.getCollectionProps() != null
                && context.getCollectionProps()
                        .containsKey(HookMetadata.ROOT_KEY)) {

            List<HookMetadata> mdHooks = null;

            try {
                mdHooks = HookMetadata.getFromJson(
                        context.getCollectionProps());
            } catch (InvalidMetadataException ime) {
                context.addWarning(ime.getMessage());
            }

            if (mdHooks != null) {
                for (HookMetadata mdHook : mdHooks) {
                    try {
                        var hookRecord = PluginsRegistry.getInstance()
                                .getHook(mdHook.getName());
                        var hook = hookRecord.getInstance();

                        var confArgs = hookRecord.getConfArgsAsBsonDocument();

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
                        context.addWarning(err);
                    }
                }
            }
        }

        next(exchange, context);
    }
}
