/*
 * uIAM - the IAM for microservices
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
package io.uiam.plugins.authorization.impl;

import io.uiam.handlers.exchange.ByteArrayRequest;
import io.uiam.plugins.authorization.PluggableAccessManager;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class FullAccessManager implements PluggableAccessManager {
    private boolean authenticationRequired;

    /**
     * this access manager allows any operation to any user
     */
    public FullAccessManager(boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
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
