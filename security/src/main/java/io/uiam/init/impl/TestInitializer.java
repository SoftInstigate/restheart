/*
 * uIAM - the IAM for microservices
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
package io.uiam.init.impl;

import io.uiam.RequestContextPredicate;
import io.uiam.handlers.RequestContext;
import io.uiam.handlers.security.AccessManagerHandler;
import io.uiam.init.Initializer;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class TestInitializer implements Initializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestInitializer.class);

    @Override
    public void init() {
        LOGGER.info("Testing initializer allows GET requests to /foo/bar");

        AccessManagerHandler.getGlobalSecurityPredicates().add(new RequestContextPredicate() {
            @Override
            public boolean resolve(HttpServerExchange hse, RequestContext context) {
                return context.isGet()
                        && "/foo/bar".equals(hse.getRequestPath());
            }
        });
    }
}
