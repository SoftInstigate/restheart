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
package org.restheart.plugins.initializers;

import io.undertow.server.HttpServerExchange;
import java.util.Map;
import org.restheart.handlers.RequestContext;
import org.restheart.plugins.GlobalTransformer;
import org.restheart.metadata.TransformerMetadata;
import org.restheart.plugins.transformers.WriteResultTransformer;
import org.restheart.handlers.RequestContextPredicate;
import org.restheart.handlers.metadata.TransformerHandler;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.RegisterPlugin;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(AddBodyToWriteResponsesInitializer.class);

    @Override
    public void init(Map<String, Object> confArgs) {
        TransformerHandler.getGlobalTransformers().add(
                new GlobalTransformer(new WriteResultTransformer(),
                        new RequestContextPredicate() {
                    @Override
                    public boolean resolve(HttpServerExchange hse, RequestContext context) {
                        return (context.isPost() && context.isCollection()) 
                                || (context.isPatch()&& context.isDocument()) 
                                || (context.isPut() && context.isDocument());
                    }
                },
                        TransformerMetadata.PHASE.RESPONSE,
                        TransformerMetadata.SCOPE.THIS,
                        null, null)
        );

        LOGGER.info("Added WriteResultTransformer as global transformer");
    }
}
