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
package org.restheart.security.plugins.interceptors;

import io.undertow.server.HttpServerExchange;
import org.restheart.plugins.RegisterPlugin;
import static org.restheart.plugins.security.InterceptPoint.RESPONSE_ASYNC;
import org.restheart.plugins.security.Interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "echoExampleAsyncResponseInterceptor",
        description = "used for testing purposes",
        enabledByDefault = false,
        requiresContent = true,
        interceptPoint = RESPONSE_ASYNC)
public class EchoExampleAsyncResponseInterceptor implements Interceptor {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(EchoExampleAsyncResponseInterceptor.class);

    @Override
    public void handle(HttpServerExchange exchange) throws Exception {
        try {
            Thread.sleep(2 * 1000);
            LOGGER.info("This log message is written 2 seconds after response "
                    + "by echoExampleAsyncResponseInterceptor");
        }
        catch (InterruptedException ie) {
            LOGGER.warn("error ", ie);
        }
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        return exchange.getRequestPath().equals("/iecho")
                || exchange.getRequestPath().equals("/piecho")
                || exchange.getRequestPath().equals("/anything");
    }

}
