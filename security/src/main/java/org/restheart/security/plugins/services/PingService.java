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
package org.restheart.security.plugins.services;

import io.undertow.server.HttpServerExchange;
import java.util.Map;
import org.restheart.ConfigurationException;
import org.restheart.handlers.exchange.JsonRequest;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Service;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "ping",
        description = "simple ping service",
        enabledByDefault = true)
public class PingService extends Service {

    private final String msg;

    /**
     *
     * @param args
     * @throws org.restheart.security.ConfigurationException
     */
    public PingService(Map<String, Object> args) throws ConfigurationException {
        super(args);
        this.msg = argValue(args, "msg");
    }

    @Override
    public String defaultUri() {
        return "/ping";
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = JsonRequest.wrap(exchange);

        if (request.isGet()) {
            exchange.setStatusCode(HttpStatus.SC_OK);
            exchange.getResponseSender().send(msg);
            exchange.endExchange();
        } else {
            exchange.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
            exchange.endExchange();
        }
    }
}
