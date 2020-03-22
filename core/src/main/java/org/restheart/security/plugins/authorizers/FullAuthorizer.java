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

import io.undertow.server.HttpServerExchange;
import java.util.Map;
import org.restheart.ConfigurationException;
import org.restheart.handlers.exchange.ByteArrayRequest;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authorizer;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "fullAuthorizer",
        description = "authorizes all requests",
        enabledByDefault = false)
public class FullAuthorizer implements Authorizer {

    private Boolean authenticationRequired;

    /**
     * this Authorizer allows any operation to any user
     *
     * @param authenticationRequired
     */
    public FullAuthorizer(Boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    /**
     * this Authorizer allows any operation to any user
     *
     * @throws org.restheart.ConfigurationException
     */
    public FullAuthorizer() throws ConfigurationException {
        this(null);
    }

    @InjectConfiguration
    public void init(Map<String, Object> confArgs) {
        this.authenticationRequired = argValue(confArgs, "authentication-required");
    }

    @Override
    public boolean isAllowed(HttpServerExchange exchange) {
        return true;
    }

    @Override
    public boolean isAuthenticationRequired(HttpServerExchange exchange) {
        return !ByteArrayRequest.wrap(exchange).isOptions()
                && (authenticationRequired == null
                        ? true
                        : authenticationRequired);
    }
}
