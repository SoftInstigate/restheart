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

package org.restheart.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.restheart.Bootstrapper;
import org.restheart.configuration.Configuration;
import org.restheart.configuration.Logging;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.TokenManager;

import ch.qos.logback.classic.Level;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.util.HttpString;
import io.undertow.util.PathMatcher;

/**
 * Proves that {@code PluginsRegistryImpl#plugSseService} actually wires
 * {@link SseWildcardInterceptorsExecutor} into the pipeline it assembles, rather than merely
 * proving (as {@code SseWildcardInterceptorsExecutorTest} does) that the executor behaves
 * correctly in isolation when handed a {@code next} handler directly.
 *
 * <p>Unlike {@code SseWildcardInterceptorsExecutorTest}, which substitutes a mock registry via
 * {@code mockStatic(PluginsRegistryImpl.class)}, this test drives a request through the real,
 * process-wide {@code PluginsRegistryImpl} singleton: {@code plugSseService} reads it via a bare
 * {@code PluginsRegistryImpl.getInstance()} call with no seam to inject a fake, so proving the
 * wiring requires exercising the real singleton end to end. To avoid the real singleton falling
 * through to {@code PluginsFactory.getInstance()} (a full classpath scan that needs a bootstrapped
 * {@code Configuration}), the lazily-initialized {@code authMechanisms}/{@code authorizers}/
 * {@code tokenManager}/{@code interceptors} fields are pre-seeded via reflection before the call
 * and restored afterwards, so this test does not leak state into others sharing the same JVM fork.
 *
 * <p>The {@code io.undertow.server.HttpServerExchange} used here is the module's existing
 * test-only shadow class (same fully-qualified name as Undertow's real, final class; this test
 * source root takes precedence over the dependency jar for that name, both at compile time and at
 * test runtime). It was extended here with {@code getRequestId()} and a working
 * {@code getSecurityContext()}/{@code setSecurityContext()} pair, the methods the real
 * {@code TracingInstrumentationHandler} and {@code SecurityInitialHandler} need that the
 * pre-existing shadow class did not yet provide; {@code Bootstrapper.getConfiguration()} is
 * mocked to return a {@code Logging} configuration with {@code requests-log-mode: 0} so
 * {@code RequestLogger} does not attempt to dump the exchange (which would need many more methods
 * the shadow does not implement, e.g. {@code getSourceAddress()}, {@code getRequestURL()}).
 *
 * <p>The request is driven through the exact handler {@code plugSseService} plugs, reached the
 * same way the framework reaches it in production: via {@code PluginsRegistry#getRootPathHandler()},
 * resolving the URI through that real Undertow {@code PathHandler}'s own {@code PathMatcher}
 * (reflectively, since {@code PathHandler#handleRequest} itself needs
 * {@code getResolvedPath()}/{@code setResolvedPath()}, which the shadow exchange does not
 * implement) rather than reimplementing the routing decision.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 * @see SseWildcardInterceptorsExecutorTest
 * @see PluginsRegistryImpl#plugSseService(PluginRecord, String, boolean)
 */
public class PlugSseServiceWiringTest {

    private static final String URI = "/__plugSseServiceWiringTest__";

    // -----------------------------------------------------------------------
    // Test fixtures
    // -----------------------------------------------------------------------

    @RegisterPlugin(name = "plugSseServiceWiringTestDenier", interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH, description = "test")
    static class DenyingAfterAuthInterceptor implements WildcardInterceptor {
        @Override
        public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
            return request.getPath() != null && request.getPath().startsWith(URI);
        }

        @Override
        public void handle(ServiceRequest<?> request, ServiceResponse<?> response) {
            response.setInError(403, "denied by plugSseService wiring test");
        }
    }

    static class RecordingSseService implements SseService {
        private final AtomicBoolean onConnectCalled = new AtomicBoolean(false);

        @Override
        public void onConnect(ServerSentEventConnection connection, String lastEventId) {
            onConnectCalled.set(true);
        }

        boolean wasOnConnectCalled() {
            return onConnectCalled.get();
        }
    }

    // -----------------------------------------------------------------------
    // Test helpers
    // -----------------------------------------------------------------------

    private static Object getField(Object target, String fieldName) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    /**
     * Resolves the {@link HttpHandler} that {@code plugSseService} plugged for the given path, by
     * reflecting into the root {@link PathHandler}'s own {@code PathMatcher} rather than calling
     * {@code PathHandler#handleRequest} (which needs exchange methods the test's shadow
     * {@code HttpServerExchange} does not implement) or reimplementing the routing decision.
     */
    @SuppressWarnings("unchecked")
    private static HttpHandler handlerPluggedFor(PathHandler rootPathHandler, String path) throws ReflectiveOperationException {
        var pathMatcherField = PathHandler.class.getDeclaredField("pathMatcher");
        pathMatcherField.setAccessible(true);
        var pathMatcher = (PathMatcher<HttpHandler>) pathMatcherField.get(rootPathHandler);
        return pathMatcher.match(path).getValue();
    }

    private static PluginRecord<Interceptor<?, ?>> interceptorRecord(WildcardInterceptor instance) {
        return new PluginRecord<>(
                instance.getClass().getSimpleName(),
                "test interceptor",
                false,
                true, // enabledByDefault
                instance.getClass().getName(),
                instance,
                null // confArgs
        );
    }

    private static Configuration configurationWithRequestLoggingDisabled() {
        // requests-log-mode: 0 and no tracing headers, so RequestLogger/TracingInstrumentationHandler
        // never call the many HttpServerExchange methods (getSourceAddress(), getRequestURL(), ...)
        // that the module's test-only shadow HttpServerExchange does not implement.
        var logging = new Logging(Level.INFO, false, null, true, true, false, List.of(), false, 0, List.of(), List.of(), 0L);
        var configuration = mock(Configuration.class);
        when(configuration.logging()).thenReturn(logging);
        return configuration;
    }

    // -----------------------------------------------------------------------
    // Test
    // -----------------------------------------------------------------------

    /**
     * Drives a request through the exact pipeline {@code plugSseService} assembles (not a
     * hand-built {@code SseWildcardInterceptorsExecutor} standing alone) and asserts that a
     * denying {@code WildcardInterceptor} registered at {@code REQUEST_AFTER_AUTH} blocks the SSE
     * handshake before the {@code SseService} is ever invoked.
     *
     * <p>This is the gap left by {@code SseWildcardInterceptorsExecutorTest}: every one of its
     * tests constructs {@code SseWildcardInterceptorsExecutor} directly, so none of them would
     * fail if the two {@code new SseWildcardInterceptorsExecutor(...)} lines were silently
     * dropped from {@code plugSseService}'s {@code pipe(...)} call.
     */
    @Test
    public void afterAuthDenyingWildcardInterceptorBlocksHandshakeThroughPlugSseServiceWiring() throws Exception {
        var registry = PluginsRegistryImpl.getInstance();

        // Save the real singleton's lazily-initialized fields so this test can restore them
        // afterwards: PluginsRegistryImpl is a process-wide singleton shared with every other
        // test in this JVM fork.
        var savedInterceptors = getField(registry, "interceptors");
        var savedAuthMechanisms = getField(registry, "authMechanisms");
        var savedAuthorizers = getField(registry, "authorizers");
        var savedTokenManager = getField(registry, "tokenManager");

        try {
            // Pre-seed the lazily-initialized fields so getAuthMechanisms()/getAuthorizers()/
            // getTokenManager()/getInterceptors() (called by plugSseService() and by
            // SseWildcardInterceptorsExecutor's constructor) do not fall through to
            // PluginsFactory.getInstance(), which performs a real classpath scan requiring a
            // fully bootstrapped Configuration.
            setField(registry, "interceptors", new LinkedHashSet<PluginRecord<Interceptor<?, ?>>>());
            setField(registry, "authMechanisms", new LinkedHashSet<PluginRecord<AuthMechanism>>());
            setField(registry, "authorizers", new LinkedHashSet<PluginRecord<Authorizer>>());
            setField(registry, "tokenManager", Optional.<PluginRecord<TokenManager>>empty());

            var denier = new DenyingAfterAuthInterceptor();
            registry.addInterceptor(interceptorRecord(denier));

            var sseService = new RecordingSseService();
            var sseRecord = new PluginRecord<SseService>(
                    "plugSseServiceWiringTestSse",
                    "test",
                    false,
                    true,
                    RecordingSseService.class.getName(),
                    sseService,
                    null);

            // Built before opening the Bootstrapper static mock below: nesting a second mock's
            // when(...)/thenReturn(...) inside the argument expression of an outer, not-yet-completed
            // when(...).thenReturn(...) chain confuses Mockito's (thread-local) stubbing state and
            // fails with "UnfinishedStubbingException".
            var configuration = configurationWithRequestLoggingDisabled();

            try (MockedStatic<Bootstrapper> bootstrapperStatic = mockStatic(Bootstrapper.class)) {
                bootstrapperStatic.when(Bootstrapper::getConfiguration).thenReturn(configuration);

                // secured = false: the FullAuthorizer path, so this test needs no real
                // authentication setup. It still runs both SseWildcardInterceptorsExecutor
                // invocations: plugSseService always adds a fullAuthorizer to the authorizers
                // set regardless of `secured`, so SecurityHandler always builds a
                // SecurityInitialHandler -> AuthorizersHandler chain (never a bare pass-through),
                // and REQUEST_BEFORE_AUTH / REQUEST_AFTER_AUTH are unconditionally part of the
                // pipe(...) call below.
                registry.plugSseService(sseRecord, URI, false);
            }

            var exchange = new HttpServerExchange();
            exchange.setRequestPath(URI + "/clock");
            exchange.setRequestMethod(new HttpString("GET"));

            var handler = handlerPluggedFor(registry.getRootPathHandler(), URI + "/clock");
            assertNotNull(handler, "plugSseService must have plugged a handler reachable from the root path handler for " + URI);

            handler.handleRequest(exchange);

            assertEquals(403, exchange.getStatusCode(),
                    "the REQUEST_AFTER_AUTH WildcardInterceptor denial must reach the exchange as plugSseService wires it, "
                            + "not just when SseWildcardInterceptorsExecutor is exercised standalone");
            assertTrue(exchange.getSentContent() != null && exchange.getSentContent().contains("denied by plugSseService wiring test"),
                    "the denial body must be sent to the client");
            assertFalse(sseService.wasOnConnectCalled(),
                    "the SSE handshake must never reach SseService.onConnect: the denial must short-circuit the pipeline");
        } finally {
            if (registry.getPipelineInfo(URI) != null) {
                registry.unplug(URI, RegisterPlugin.MATCH_POLICY.PREFIX);
            }
            registry.removeInterceptorIf(i -> i.getInstance() instanceof DenyingAfterAuthInterceptor);

            setField(registry, "interceptors", savedInterceptors);
            setField(registry, "authMechanisms", savedAuthMechanisms);
            setField(registry, "authorizers", savedAuthorizers);
            setField(registry, "tokenManager", savedTokenManager);

            assertNoLeakedPipeline(registry);
        }
    }

    /**
     * Confirms {@code unplug} left no trace of this test's pipeline in the shared registry, so it
     * cannot affect other tests in this JVM fork.
     */
    private static void assertNoLeakedPipeline(PluginsRegistryImpl registry) throws ReflectiveOperationException {
        assertEquals(null, handlerPluggedFor(registry.getRootPathHandler(), URI + "/clock"),
                "unplug() must remove the handler plugged for " + URI + " from the root path handler");
    }
}
