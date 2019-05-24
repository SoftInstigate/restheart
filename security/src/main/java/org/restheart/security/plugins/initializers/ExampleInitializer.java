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
package org.restheart.security.plugins.initializers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.restheart.security.handlers.exchange.JsonRequest;
import org.restheart.security.handlers.GlobalSecuirtyPredicatesAuthorizer;
import org.restheart.security.plugins.PluginsRegistry;
import org.restheart.security.plugins.interceptors.EchoExampleRequestInterceptor;
import org.restheart.security.plugins.interceptors.EchoExampleResponseInterceptor;
import org.restheart.security.plugins.interceptors.ExampleProxiedRequestInterceptor;
import org.restheart.security.plugins.interceptors.ExampleProxiedResponseInterceptor;
import org.restheart.security.utils.URLUtils;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;
import org.restheart.security.plugins.Initializer;
import org.restheart.security.plugins.RegisterPlugin;

/**
 * Just an example initializer. It is not enabledByDefault; to enable it add to
 * configuration file:<br>
 * <pre>
 * plugins-args:
 *     testInitializer:
 *         enabled: true
 * </pre>
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "testInitializer",
        priority = 100,
        description = "Just an example initializer",
        enabledByDefault = false)
public class ExampleInitializer implements Initializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExampleInitializer.class);

    @Override
    public void init() {
        LOGGER.info("Testing initializer");
        LOGGER.info("\tdenies GET /secho/foo using a Global Permission Predicate");
        LOGGER.info("\tadds a request and a response interceptors for /iecho and /siecho");

        // add a global security predicate
        GlobalSecuirtyPredicatesAuthorizer.getGlobalSecurityPredicates().add(new Predicate() {
            @Override
            public boolean resolve(HttpServerExchange exchange) {
                var request = JsonRequest.wrap(exchange);

                return !(request.isGet()
                        && "/secho/foo".equals(URLUtils.removeTrailingSlashes(
                                exchange.getRequestPath())));
            }
        });

        // add an example response interceptor
        PluginsRegistry
                .getInstance()
                .getResponseInterceptors()
                .add(new EchoExampleResponseInterceptor());

        // add an example request interceptor
        PluginsRegistry
                .getInstance()
                .getRequestInterceptors()
                .add(new EchoExampleRequestInterceptor());

        // add an exampe request interceptor
        PluginsRegistry
                .getInstance()
                .getRequestInterceptors()
                .add(new ExampleProxiedRequestInterceptor());

        // add an exampe response interceptor
        PluginsRegistry
                .getInstance()
                .getResponseInterceptors()
                .add(new ExampleProxiedResponseInterceptor());
    }
}
