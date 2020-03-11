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
package org.restheart.mongodb.plugins.initializers;

import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.mongodb.plugins.transformers.WriteResultTransformer;
import org.restheart.plugins.mongodb.GlobalTransformer;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mongodb.Transformer.PHASE;
import org.restheart.plugins.mongodb.Transformer.SCOPE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "addBodyToWriteResponsesInitializer",
        priority = 100,
        description = "Add writeResult to global transformers",
        enabledByDefault = false)
public class AddBodyToWriteResponsesInitializer implements Initializer {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(AddBodyToWriteResponsesInitializer.class);

    private final PluginsRegistry pluginsRegistry;
    
    @InjectPluginsRegistry
    public AddBodyToWriteResponsesInitializer(PluginsRegistry pluginRegistry) {
        this.pluginsRegistry = pluginRegistry;
    }
    
    /**
     *
     */
    @Override
    public void init() {
        pluginsRegistry.getGlobalTransformers().add(
                new GlobalTransformer(
                        new WriteResultTransformer(), (HttpServerExchange hse,
                                RequestContext context)
                        -> (context.isPost() && context.isCollection())
                        || (context.isPatch() && context.isDocument())
                        || (context.isPut() && context.isDocument()),
                        PHASE.RESPONSE,
                        SCOPE.THIS,
                        null, null)
        );

        LOGGER.info("Added WriteResultTransformer as global transformer");
    }
}
