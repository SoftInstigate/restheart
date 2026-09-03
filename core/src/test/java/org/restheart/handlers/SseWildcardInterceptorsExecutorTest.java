package org.restheart.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * Proves that {@link SseWildcardInterceptorsExecutor} wires {@link WildcardInterceptor}s
 * into the SSE handshake pipeline: it invokes them in the right place, honors {@code resolve()},
 * excludes non-wildcard interceptors, and lets a {@code REQUEST_AFTER_AUTH} denial short-circuit
 * the pipeline while a {@code REQUEST_BEFORE_AUTH} one does not.
 *
 * <p>{@code PluginsRegistryImpl} is a process-wide singleton reached via a static
 * {@code getInstance()} call, and {@link SseWildcardInterceptorsExecutor} reads it directly in
 * its constructor (mirroring the existing {@code BeforeExchangeInitInterceptorsExecutor}, which
 * has no unit test of its own). There is no seam to inject a fake registry, so these tests use
 * {@code mockStatic(PluginsRegistryImpl.class)} to substitute a mock registry for the duration of
 * each test, rather than mutating the real singleton (which is shared across the whole JVM).
 *
 * <p>{@code HttpServerExchange} is exercised via the module's existing test-only shadow class at
 * {@code io.undertow.server.HttpServerExchange} (same fully-qualified name as Undertow's real,
 * final class; this test source root takes precedence over the dependency jar for that name, both
 * at compile time and at test runtime, so production code under test binds to the very same fake
 * instance this test constructs). It was extended here with {@code getResponseHeaders()} and a
 * recording {@code getResponseSender()}, the two methods this executor needs that the pre-existing
 * shadow class did not yet provide.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class SseWildcardInterceptorsExecutorTest {

    // -----------------------------------------------------------------------
    // Test fixtures
    // -----------------------------------------------------------------------

    @RegisterPlugin(name = "afterAuthWildcard", interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH, description = "test")
    static class AfterAuthWildcardInterceptor implements WildcardInterceptor {
        private final List<String> calls;
        private final boolean resolves;
        private final Integer denyCode;

        AfterAuthWildcardInterceptor(List<String> calls, boolean resolves, Integer denyCode) {
            this.calls = calls;
            this.resolves = resolves;
            this.denyCode = denyCode;
        }

        @Override
        public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
            return resolves;
        }

        @Override
        public void handle(ServiceRequest<?> request, ServiceResponse<?> response) {
            calls.add("afterAuthWildcard");
            if (denyCode != null) {
                response.setInError(denyCode, "denied by test interceptor", null);
            }
        }
    }

    @RegisterPlugin(name = "beforeAuthWildcard", interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH, description = "test")
    static class BeforeAuthWildcardInterceptor implements WildcardInterceptor {
        private final List<String> calls;
        private final Integer denyCode;

        BeforeAuthWildcardInterceptor(List<String> calls, Integer denyCode) {
            this.calls = calls;
            this.denyCode = denyCode;
        }

        @Override
        public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
            return true;
        }

        @Override
        public void handle(ServiceRequest<?> request, ServiceResponse<?> response) {
            calls.add("beforeAuthWildcard");
            if (denyCode != null) {
                response.setInError(denyCode, "denied before auth", null);
            }
        }
    }

    @RegisterPlugin(name = "nonWildcard", interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH, description = "must never run on the SSE path")
    static class NonWildcardInterceptor implements Interceptor<ServiceRequest<?>, ServiceResponse<?>> {
        private final List<String> calls;

        NonWildcardInterceptor(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
            return true;
        }

        @Override
        public void handle(ServiceRequest<?> request, ServiceResponse<?> response) {
            calls.add("nonWildcard");
        }
    }

    /** Records invocation order; stands in for the next handler in the pipeline (e.g. the security handler). */
    static class RecordingHandler extends PipelinedHandler {
        private final List<String> calls;
        private final String label;

        RecordingHandler(List<String> calls, String label) {
            super(null);
            this.calls = calls;
            this.label = label;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            calls.add(label);
            next(exchange);
        }
    }

    // -----------------------------------------------------------------------
    // Test helpers
    // -----------------------------------------------------------------------

    private static HttpServerExchange fakeExchange() {
        var exchange = new HttpServerExchange();
        exchange.setRequestPath("/sse/clock");
        exchange.setRequestMethod(new HttpString("GET"));
        return exchange;
    }

    private static PluginRecord<Interceptor<?, ?>> record(Interceptor<?, ?> instance) {
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

    /**
     * Substitutes {@code PluginsRegistryImpl.getInstance()} with a mock reporting the given
     * interceptors, for the lifetime of the returned {@link AutoCloseable}.
     */
    private static AutoCloseable registryReturning(Set<PluginRecord<Interceptor<?, ?>>> interceptors) {
        var registryMock = mock(PluginsRegistryImpl.class);
        when(registryMock.getInterceptors()).thenReturn(interceptors);

        var registryStatic = mockStatic(PluginsRegistryImpl.class);
        registryStatic.when(PluginsRegistryImpl::getInstance).thenReturn(registryMock);
        return registryStatic;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void afterAuthWildcardInterceptorIsInvokedAndDenialTakesEffect() throws Exception {
        var calls = new ArrayList<String>();
        var denying = new AfterAuthWildcardInterceptor(calls, true, 403);

        try (var registryStatic = registryReturning(Set.of(record(denying)))) {
            var next = new RecordingHandler(calls, "next");
            var executor = new SseWildcardInterceptorsExecutor(next, InterceptPoint.REQUEST_AFTER_AUTH);
            var exchange = fakeExchange();

            executor.handleRequest(exchange);

            assertEquals(List.of("afterAuthWildcard"), calls,
                    "the interceptor must have run, and the pipeline must stop there (next must not run)");
            assertTrue(Exchange.isInError(exchange), "the exchange must be flagged in error");
            assertEquals(403, exchange.getStatusCode(), "the denial status code must be written to the exchange");
            assertTrue(exchange.getSentContent() != null && exchange.getSentContent().contains("denied by test interceptor"),
                    "the denial body must be sent to the client");
        }
    }

    @Test
    public void beforeAuthWildcardInterceptorRunsBeforeTheNextHandlerAndDenialHasNoEffect() throws Exception {
        var calls = new ArrayList<String>();
        // even though this interceptor calls setInError, REQUEST_BEFORE_AUTH denial must not
        // short-circuit the pipeline (mirrors RequestInterceptorsExecutor: denying pre-auth would
        // let an unauthenticated client learn something about the endpoint from the error body).
        var beforeAuth = new BeforeAuthWildcardInterceptor(calls, 403);

        try (var registryStatic = registryReturning(Set.of(record(beforeAuth)))) {
            var securityHandlerStandIn = new RecordingHandler(calls, "securityHandler");
            var executor = new SseWildcardInterceptorsExecutor(securityHandlerStandIn, InterceptPoint.REQUEST_BEFORE_AUTH);
            var exchange = fakeExchange();

            executor.handleRequest(exchange);

            assertEquals(List.of("beforeAuthWildcard", "securityHandler"), calls,
                    "the interceptor must run, then hand off to the next handler (the security handler)");
        }
    }

    @Test
    public void interceptorWhoseResolveReturnsFalseIsNotInvoked() throws Exception {
        var calls = new ArrayList<String>();
        var unresolved = new AfterAuthWildcardInterceptor(calls, false, null);

        try (var registryStatic = registryReturning(Set.of(record(unresolved)))) {
            var next = new RecordingHandler(calls, "next");
            var executor = new SseWildcardInterceptorsExecutor(next, InterceptPoint.REQUEST_AFTER_AUTH);
            var exchange = fakeExchange();

            executor.handleRequest(exchange);

            assertFalse(calls.contains("afterAuthWildcard"), "an interceptor whose resolve() returns false must not be handled");
            assertEquals(List.of("next"), calls, "the pipeline must still proceed to the next handler");
        }
    }

    @Test
    public void nonWildcardInterceptorIsNotInvokedOnTheSsePath() throws Exception {
        var calls = new ArrayList<String>();
        var wildcard = new AfterAuthWildcardInterceptor(calls, true, null);
        var plain = new NonWildcardInterceptor(calls);

        var interceptors = new LinkedHashSet<PluginRecord<Interceptor<?, ?>>>();
        interceptors.add(record(wildcard));
        interceptors.add(record(plain));

        try (var registryStatic = registryReturning(interceptors)) {
            var next = new RecordingHandler(calls, "next");
            var executor = new SseWildcardInterceptorsExecutor(next, InterceptPoint.REQUEST_AFTER_AUTH);
            var exchange = fakeExchange();

            executor.handleRequest(exchange);

            assertFalse(calls.contains("nonWildcard"),
                    "a plain Interceptor<ServiceRequest<?>, ServiceResponse<?>> must be excluded from the SSE path");
            assertEquals(List.of("afterAuthWildcard", "next"), calls,
                    "only the WildcardInterceptor must run, then the pipeline proceeds");
        }
    }

    @Test
    public void noWildcardInterceptorsRegisteredForcesFastPathToNext() throws Exception {
        try (var registryStatic = registryReturning(Set.of())) {
            var calls = new ArrayList<String>();
            var next = new RecordingHandler(calls, "next");
            var executor = new SseWildcardInterceptorsExecutor(next, InterceptPoint.REQUEST_AFTER_AUTH);
            var exchange = fakeExchange();

            executor.handleRequest(exchange);

            assertEquals(List.of("next"), calls);
        }
    }
}
