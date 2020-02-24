/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.plugins.authorizers;

import org.restheart.security.handlers.exchange.ByteArrayRequest;
import io.undertow.server.HttpServerExchange;
import java.util.Map;
import org.restheart.security.ConfigurationException;
import org.restheart.security.plugins.Authorizer;
import static org.restheart.security.plugins.ConfigurablePlugin.argValue;
import org.restheart.security.plugins.RegisterPlugin;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "fullAuthorizer",
        description = "authorizes all requests",
        enabledByDefault = false)
public class FullAuthorizer implements Authorizer {

    private final boolean authenticationRequired;

    /**
     * this Authorizer allows any operation to any user
     *
     * @param authenticationRequired
     */
    public FullAuthorizer(boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }
    
    /**
     * this Authorizer allows any operation to any user
     *
     * @param confArgs
     * @throws org.restheart.security.ConfigurationException
     */
    public FullAuthorizer(Map<String, Object> confArgs) throws ConfigurationException {
        this((boolean) argValue(confArgs, "authentication-required"));
    }

    @Override
    public boolean isAllowed(HttpServerExchange exchange) {
        return true;
    }

    @Override
    public boolean isAuthenticationRequired(HttpServerExchange exchange) {
        return !ByteArrayRequest.wrap(exchange).isOptions()
                && authenticationRequired;
    }
}
