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
package io.uiam.plugins.init.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uiam.handlers.exchange.JsonRequest;
import io.uiam.handlers.security.AccessManagerHandler;
import io.uiam.plugins.PluginsRegistry;
import io.uiam.plugins.init.PluggableInitializer;
import io.uiam.plugins.interceptors.impl.EchoExampleRequestInterceptor;
import io.uiam.plugins.interceptors.impl.EchoExampleResponseInterceptor;
import io.uiam.plugins.interceptors.impl.ExampleProxiedRequestInterceptor;
import io.uiam.plugins.interceptors.impl.ExampleProxiedResponseInterceptor;
import io.uiam.utils.URLUtils;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ExampleInitializer implements PluggableInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExampleInitializer.class);

    @Override
    public void init() {
        LOGGER.info("Testing initializer");
        LOGGER.info("\tdenies GET /secho/foo using a Global Permission Predicate");
        LOGGER.info("\tadds a request and a response interceptors for /iecho and /siecho");

        // add a global security predicate
        AccessManagerHandler.getGlobalSecurityPredicates().add(new Predicate() {
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
