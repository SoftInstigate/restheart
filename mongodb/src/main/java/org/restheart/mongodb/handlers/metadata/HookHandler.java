/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.handlers.metadata;

import io.undertow.server.HttpServerExchange;
import java.util.List;
import java.util.NoSuchElementException;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.mongodb.metadata.HookMetadata;
import org.restheart.plugins.BsonInterceptor;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * handler that executes the hooks defined in the collection properties
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "hooksExecutor",
        description = "executes the hooks",
        interceptPoint = InterceptPoint.RESPONSE_ASYNC)
public class HookHandler implements BsonInterceptor {

    static final Logger LOGGER
            = LoggerFactory.getLogger(HookHandler.class);

    private PluginsRegistry pluginsRegistry;

    @InjectPluginsRegistry
    public void init(PluginsRegistry pluginsRegistry) {
        this.pluginsRegistry = pluginsRegistry;
    }

    @Override
    public void handle(BsonRequest request, BsonResponse response) throws Exception {
        var exchange = request.getExchange();
        var context = RequestContext.wrap(request, response);

        // execute global hooks
        executeGlobalHooks(exchange, context);

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
                        var _hookRecord = pluginsRegistry
                                .getHooks()
                                .stream()
                                .filter(t -> mdHook.getName().equals(t.getName()))
                                .findFirst();

                        if (_hookRecord.isPresent()) {
                            var hookRecord = _hookRecord.get();
                            var hook = hookRecord.getInstance();

                            var confArgs = JsonUtils.toBsonDocument(
                                    hookRecord.getConfArgs());

                            if (hook.doesSupportRequests(context)) {
                                hook.hook(exchange,
                                        context,
                                        mdHook.getArgs(),
                                        confArgs);
                            }
                        } else {
                            LOGGER.warn("Hook set to apply "
                                    + "but not registered: {}", mdHook.getName());
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
    }

    @Override
    public boolean resolve(BsonRequest request, BsonResponse response) {
        return true;
    }
    
    private void executeGlobalHooks(HttpServerExchange exchange, 
            RequestContext context) {
        // execute global request tranformers
        pluginsRegistry.getGlobalHooks().stream()
                .forEachOrdered(gh -> gh.hook(exchange, context));
    }
}
