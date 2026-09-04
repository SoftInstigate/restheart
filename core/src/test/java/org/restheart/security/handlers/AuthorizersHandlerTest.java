/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.security.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.Service;
import org.restheart.plugins.security.RequestDescriptor;

import io.undertow.server.HttpServerExchange;

/** Covers restheart#722's override-detection seam — the rest of {@link AuthorizersHandler} needs a real exchange to exercise. */
public class AuthorizersHandlerTest {

    private static class PlainService implements Service<ServiceRequest<?>, ServiceResponse<?>> {
        @Override public Consumer<HttpServerExchange> requestInitializer() { return null; }
        @Override public Consumer<HttpServerExchange> responseInitializer() { return null; }
        @Override public Function<HttpServerExchange, ServiceRequest<?>> request() { return null; }
        @Override public Function<HttpServerExchange, ServiceResponse<?>> response() { return null; }
    }

    private static final class OverridingService extends PlainService {
        @Override
        public List<RequestDescriptor> operationsToAuthorize(HttpServerExchange exchange) {
            return List.of();
        }
    }

    @Test
    public void serviceUsingTheDefault_isNotDetectedAsOverriding() {
        assertFalse(AuthorizersHandler.overridesOperationsToAuthorize(new PlainService()));
    }

    @Test
    public void serviceOverridingOperationsToAuthorize_isDetected() {
        assertTrue(AuthorizersHandler.overridesOperationsToAuthorize(new OverridingService()));
    }
}
