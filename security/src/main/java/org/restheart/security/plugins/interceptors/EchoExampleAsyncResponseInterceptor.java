/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
package org.restheart.security.plugins.interceptors;

import io.undertow.server.HttpServerExchange;
import static org.restheart.plugins.InterceptPoint.RESPONSE_ASYNC;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.RegisterPlugin;
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
