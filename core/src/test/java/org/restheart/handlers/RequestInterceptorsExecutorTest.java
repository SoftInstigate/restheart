/*-
 * ========================LICENSE_START=================================
 * restheart
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

package org.restheart.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.exchange.PipelineInfo;
import org.restheart.exchange.PipelineInfo.PIPELINE_TYPE;
import org.restheart.exchange.Request;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * Proves that {@link RequestInterceptorsExecutor} defers, rather than drops, a denial raised
 * via {@code response.setInError(...)} at {@code REQUEST_BEFORE_AUTH}: the denial must still be
 * observed and sent once the {@code REQUEST_AFTER_AUTH} invocation of this executor runs, even
 * when that invocation resolves zero interceptors of its own for the handling service
 * (GitHub issue #717).
 *
 * <p>{@code PluginsRegistryImpl} is a process-wide singleton reached via a static
 * {@code getInstance()} call, and {@link RequestInterceptorsExecutor} reads it directly in its
 * constructor and in {@code handleRequest}. There is no seam to inject a fake registry, so these
 * tests use {@code mockStatic(PluginsRegistryImpl.class)} to substitute a mock registry for the
 * duration of each test, rather than mutating the real singleton (which is shared across the
 * whole JVM).
 *
 * <p>The scenario is driven on the SERVICE pipeline (a fake {@link Service} plus
 * {@link StringRequest}/{@link StringResponse}) rather than the PROXY pipeline: the PROXY
 * response type ({@code ByteArrayProxyResponse}) buffers its content in pooled, connection-backed
 * buffers that this test's fake {@code HttpServerExchange} has no connection for, whereas
 * {@code StringResponse} keeps its content in a plain field. This is purely a test-construction
 * choice; it exercises the exact same {@code handlingService != null} branch, and the same
 * terminal deny-and-send/next decision, that {@link RequestInterceptorsExecutor} runs on the
 * SERVICE pipeline in production.
 *
 * <p>{@code HttpServerExchange} is exercised via the module's existing test-only shadow class at
 * {@code io.undertow.server.HttpServerExchange} (same fully-qualified name as Undertow's real,
 * final class; this test source root takes precedence over the dependency jar for that name, both
 * at compile time and at test runtime, so production code under test binds to the very same fake
 * instance this test constructs). It was extended here with {@code isResponseStarted()}, the one
 * method {@link ResponseSender} (invoked from the terminal deny branch) needs that the
 * pre-existing shadow class did not yet provide; the fake always reports {@code false}, matching
 * its existing behavior of never actually starting a response.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class RequestInterceptorsExecutorTest {

    // -----------------------------------------------------------------------
    // Test fixtures
    // -----------------------------------------------------------------------

    @RegisterPlugin(name = "denyingBeforeAuth", description = "denies at REQUEST_BEFORE_AUTH")
    static class DenyingBeforeAuthInterceptor implements Interceptor<ServiceRequest<?>, ServiceResponse<?>> {
        private final List<String> calls;

        DenyingBeforeAuthInterceptor(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
            return true;
        }

        @Override
        public void handle(ServiceRequest<?> request, ServiceResponse<?> response) {
            calls.add("denyingBeforeAuth");
            // mirrors CollectionPropsInjector/DbPropsInjector/ContentSizeChecker/BruteForceAttackGuard:
            // deny before auth, don't send yet (see RequestInterceptorsExecutor's REQUEST_AFTER_AUTH check)
            response.setInError(403, "denied before auth", null);
        }
    }

    @RegisterPlugin(name = "nonResolvingAfterAuth", description = "registered at REQUEST_AFTER_AUTH but never resolves")
    static class NonResolvingAfterAuthInterceptor implements Interceptor<ServiceRequest<?>, ServiceResponse<?>> {
        private final List<String> calls;

        NonResolvingAfterAuthInterceptor(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
            return false;
        }

        @Override
        public void handle(ServiceRequest<?> request, ServiceResponse<?> response) {
            calls.add("nonResolvingAfterAuth");
        }
    }

    @RegisterPlugin(name = "testService", description = "test")
    static class TestService implements Service<StringRequest, StringResponse> {
        @Override
        public Consumer<HttpServerExchange> requestInitializer() {
            return StringRequest::init;
        }

        @Override
        public Consumer<HttpServerExchange> responseInitializer() {
            return StringResponse::init;
        }

        @Override
        public Function<HttpServerExchange, StringRequest> request() {
            return StringRequest::of;
        }

        @Override
        public Function<HttpServerExchange, StringResponse> response() {
            return StringResponse::of;
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

    private static final String SERVICE_NAME = "testService";
    private static final String PATH = "/test";

    /**
     * Builds a fake exchange wired as a SERVICE-pipeline request for {@link #SERVICE_NAME}:
     * pipeline info attached, and the request/response pair created and attached exactly once,
     * mirroring what {@code ServiceExchangeInitializer} does in production before either
     * {@code RequestInterceptorsExecutor} invocation runs.
     */
    private static HttpServerExchange fakeServiceExchange() {
        var exchange = new HttpServerExchange();
        exchange.setRequestPath(PATH);
        exchange.setRequestMethod(new HttpString("GET"));

        Request.setPipelineInfo(exchange, new PipelineInfo(PIPELINE_TYPE.SERVICE, PATH, SERVICE_NAME));
        StringRequest.init(exchange);
        StringResponse.init(exchange);

        return exchange;
    }

    private static PluginRecord<Service<?, ?>> serviceRecord(Service<?, ?> instance) {
        return new PluginRecord<>(
                SERVICE_NAME,
                "test service",
                false,
                true, // enabledByDefault
                instance.getClass().getName(),
                instance,
                null // confArgs
        );
    }

    /**
     * Substitutes {@code PluginsRegistryImpl.getInstance()} with a mock wired for the
     * {@link #SERVICE_NAME} SERVICE pipeline: the service lookups {@link RequestInterceptorsExecutor}
     * and {@link ResponseSender} need, plus the given interceptor lists per {@link InterceptPoint}.
     */
    private static AutoCloseable registryReturning(Service<?, ?> service,
            List<Interceptor<?, ?>> beforeAuthInterceptors, List<Interceptor<?, ?>> afterAuthInterceptors) {
        var record = serviceRecord(service);

        var registryMock = mock(PluginsRegistryImpl.class);
        when(registryMock.getService(SERVICE_NAME)).thenReturn(record);
        when(registryMock.getServices()).thenReturn(Set.of(record));
        when(registryMock.getPipelineInfo(PATH)).thenReturn(new PipelineInfo(PIPELINE_TYPE.SERVICE, PATH, SERVICE_NAME));
        when(registryMock.getServiceInterceptors(same(service), eq(InterceptPoint.REQUEST_BEFORE_AUTH))).thenReturn(beforeAuthInterceptors);
        when(registryMock.getServiceInterceptors(same(service), eq(InterceptPoint.REQUEST_AFTER_AUTH))).thenReturn(afterAuthInterceptors);

        var registryStatic = mockStatic(PluginsRegistryImpl.class);
        registryStatic.when(PluginsRegistryImpl::getInstance).thenReturn(registryMock);
        return registryStatic;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * The regression test for GitHub issue #717: nothing is registered at
     * {@code REQUEST_AFTER_AUTH}, so that invocation's {@code interceptors} list is genuinely
     * empty. The pending denial raised at {@code REQUEST_BEFORE_AUTH} must still be observed and
     * sent: this must not depend on some other, unrelated interceptor happening to be registered
     * at {@code REQUEST_AFTER_AUTH} for this service (in a stock build, interceptors such as
     * DateHeader/XPoweredBy mask this by always being registered there by default).
     */
    @Test
    public void beforeAuthDenialIsDeferredAndSentAtAfterAuthWhenNoAfterAuthInterceptorsRegistered() throws Exception {
        var calls = new ArrayList<String>();
        var denying = new DenyingBeforeAuthInterceptor(calls);
        var service = new TestService();

        try (var registryStatic = registryReturning(service, List.of(denying), List.of())) {
            var exchange = fakeServiceExchange();

            var nextHandlerStandIn = new RecordingHandler(calls, "next");
            var afterAuthExecutor = new RequestInterceptorsExecutor(InterceptPoint.REQUEST_AFTER_AUTH);
            var securityHandlerStandIn = new RecordingHandler(calls, "securityHandler");
            var beforeAuthExecutor = new RequestInterceptorsExecutor(InterceptPoint.REQUEST_BEFORE_AUTH);

            // wires: beforeAuthExecutor -> securityHandlerStandIn -> afterAuthExecutor -> nextHandlerStandIn
            var pipeline = PipelinedHandler.pipe(beforeAuthExecutor, securityHandlerStandIn, afterAuthExecutor, nextHandlerStandIn);

            pipeline.handleRequest(exchange);

            assertEquals(List.of("denyingBeforeAuth", "securityHandler"), calls,
                    "the before-auth interceptor and the security handler must run, but the pipeline must stop "
                            + "at REQUEST_AFTER_AUTH once the deferred denial is observed, even though REQUEST_AFTER_AUTH "
                            + "has no interceptors of its own for this service: the final next handler must never run");
            assertTrue(Exchange.isInError(exchange), "the exchange must be flagged in error");
            assertEquals(403, exchange.getStatusCode(),
                    "the status code set by the REQUEST_BEFORE_AUTH denial must be the one sent after auth");
            assertTrue(exchange.getSentContent() != null && exchange.getSentContent().contains("denied before auth"),
                    "the body set by the REQUEST_BEFORE_AUTH denial must be the one sent after auth, not lost");
        }
    }

    /**
     * Positive control: an (unrelated, non-resolving) interceptor is registered at
     * {@code REQUEST_AFTER_AUTH}, so that invocation's {@code interceptors} list is non-empty.
     * This passes with or without the fix and proves nothing about the defect on its own; it
     * exists to show the difference from the negative control above, which is the one that
     * actually exercises the fix.
     */
    @Test
    public void beforeAuthDenialIsDeferredAndSentAtAfterAuthWithAfterAuthInterceptorsRegistered() throws Exception {
        var calls = new ArrayList<String>();
        var denying = new DenyingBeforeAuthInterceptor(calls);
        var nonResolving = new NonResolvingAfterAuthInterceptor(calls);
        var service = new TestService();

        try (var registryStatic = registryReturning(service, List.of(denying), List.of(nonResolving))) {
            var exchange = fakeServiceExchange();

            var nextHandlerStandIn = new RecordingHandler(calls, "next");
            var afterAuthExecutor = new RequestInterceptorsExecutor(InterceptPoint.REQUEST_AFTER_AUTH);
            var securityHandlerStandIn = new RecordingHandler(calls, "securityHandler");
            var beforeAuthExecutor = new RequestInterceptorsExecutor(InterceptPoint.REQUEST_BEFORE_AUTH);

            var pipeline = PipelinedHandler.pipe(beforeAuthExecutor, securityHandlerStandIn, afterAuthExecutor, nextHandlerStandIn);

            pipeline.handleRequest(exchange);

            assertEquals(List.of("denyingBeforeAuth", "securityHandler"), calls,
                    "the REQUEST_AFTER_AUTH interceptor must be considered but not handled (its resolve() declines), "
                            + "and the pipeline must stop at REQUEST_AFTER_AUTH once the deferred denial is observed");
            assertEquals(403, exchange.getStatusCode(), "the deferred denial's status code must still be sent");
            assertTrue(exchange.getSentContent() != null && exchange.getSentContent().contains("denied before auth"),
                    "the deferred denial's body must still be sent");
        }
    }

    /**
     * Sanity check that the empty-list fast path still reaches {@code next(exchange)} normally
     * when there is no pending denial at all: the restructuring must not turn every request into
     * a deny-and-send.
     */
    @Test
    public void noInterceptorsAtEitherPointForcesFastPathToNext() throws Exception {
        var service = new TestService();

        try (var registryStatic = registryReturning(service, List.of(), List.of())) {
            var calls = new ArrayList<String>();
            var exchange = fakeServiceExchange();

            var nextHandlerStandIn = new RecordingHandler(calls, "next");
            var afterAuthExecutor = new RequestInterceptorsExecutor(InterceptPoint.REQUEST_AFTER_AUTH);
            var securityHandlerStandIn = new RecordingHandler(calls, "securityHandler");
            var beforeAuthExecutor = new RequestInterceptorsExecutor(InterceptPoint.REQUEST_BEFORE_AUTH);

            var pipeline = PipelinedHandler.pipe(beforeAuthExecutor, securityHandlerStandIn, afterAuthExecutor, nextHandlerStandIn);

            pipeline.handleRequest(exchange);

            assertEquals(List.of("securityHandler", "next"), calls, "with nothing denying, the whole pipeline must run through to the final next handler");
            assertTrue(!Exchange.isInError(exchange), "the exchange must not be flagged in error");
        }
    }
}
