package org.restheart.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.restheart.exchange.Exchange;
import org.restheart.exchange.SseHandshakeRequest;
import org.restheart.exchange.SseHandshakeResponse;
import org.restheart.logging.RequestPhaseContext;
import org.restheart.logging.RequestPhaseContext.Phase;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.InterceptorException;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.LambdaUtils;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;

/**
 * Runs the enabled {@link WildcardInterceptor}s for a given {@link InterceptPoint}
 * on the SSE handshake pipeline (see {@code PluginsRegistryImpl#plugSseService}).
 *
 * <p>{@code SseService} is a {@code Plugin}, not an {@code ExchangeTypeResolver}: an
 * SSE service declares no request/response types, so the type-equality check that
 * {@code RequestInterceptorsExecutor} uses to admit interceptors on the SERVICE
 * pipeline has nothing to compare against on this path, and the PROXY-pipeline
 * fallback only admits {@code ByteArrayProxyRequest}/{@code ByteArrayProxyResponse}
 * typed interceptors. {@code WildcardInterceptor} is the only interceptor category
 * that is meaningful here, so this executor admits only those, deliberately mirroring
 * the shape of {@link BeforeExchangeInitInterceptorsExecutor} (which solves the
 * analogous problem for {@code REQUEST_BEFORE_EXCHANGE_INIT}) rather than reusing
 * {@link RequestInterceptorsExecutor}, which several other handlers key off the
 * SERVICE/PROXY {@code PipelineInfo} type and must stay untouched.
 *
 * <p>Interceptors are invoked with {@code SseHandshakeRequest}/{@code SseHandshakeResponse},
 * a minimal concrete {@code ServiceRequest}/{@code ServiceResponse} pair that exposes path,
 * headers, query parameters and the authenticated account, and (on the response side) a
 * working {@code setInError}.
 *
 * <p>Denial only takes effect at {@code REQUEST_AFTER_AUTH}, mirroring
 * {@link RequestInterceptorsExecutor}: denying at {@code REQUEST_BEFORE_AUTH} would let an
 * unauthenticated request learn something about the endpoint from the error response.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 * @see WildcardInterceptor
 * @see SseHandshakeRequest
 * @see SseHandshakeResponse
 */
public class SseWildcardInterceptorsExecutor extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SseWildcardInterceptorsExecutor.class);

    private final InterceptPoint interceptPoint;

    private final List<WildcardInterceptor> wildcardInterceptors;

    /**
     * Creates a new executor with no next handler.
     *
     * @param interceptPoint the intercept point this executor runs interceptors for
     */
    public SseWildcardInterceptorsExecutor(InterceptPoint interceptPoint) {
        this(null, interceptPoint);
    }

    /**
     * Creates a new executor.
     *
     * @param next the next handler in the pipeline
     * @param interceptPoint the intercept point this executor runs interceptors for
     */
    public SseWildcardInterceptorsExecutor(PipelinedHandler next, InterceptPoint interceptPoint) {
        super(next);
        this.interceptPoint = interceptPoint;

        PluginsRegistry pluginsRegistry = PluginsRegistryImpl.getInstance();

        this.wildcardInterceptors = pluginsRegistry.getInterceptors().stream()
                .filter(PluginRecord::isEnabled)
                .map(PluginRecord::getInstance)
                .filter(i -> i instanceof WildcardInterceptor)
                .filter(i -> PluginUtils.interceptPoint(i) == interceptPoint)
                .map(i -> (WildcardInterceptor) i)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (this.wildcardInterceptors.isEmpty()) {
            LOGGER.debug("{} SSE WILDCARD INTERCEPTORS: none registered", interceptPoint);
            next(exchange);
            return;
        }

        var request = SseHandshakeRequest.of(exchange);
        var response = SseHandshakeResponse.of(exchange);

        RequestPhaseContext.setPhase(Phase.PHASE_START);
        LOGGER.debug("{} SSE WILDCARD INTERCEPTORS for {} {}", interceptPoint, exchange.getRequestMethod(), exchange.getRequestPath());

        List<WildcardInterceptor> resolvedInterceptors = null;
        for (var ri : this.wildcardInterceptors) {
            try {
                if (ri.resolve(request, response)) {
                    if (resolvedInterceptors == null) {
                        resolvedInterceptors = new ArrayList<>(this.wildcardInterceptors.size());
                    }
                    resolvedInterceptors.add(ri);
                }
            } catch (Exception ex) {
                LOGGER.warn("Error resolving interceptor {} for {} on intercept point {}", ri.getClass().getSimpleName(), exchange.getRequestPath(), interceptPoint, ex);

                Exchange.setInError(exchange);
                LambdaUtils.throwsSneakyException(new InterceptorException("Error resolving interceptor " + ri.getClass().getSimpleName(), ex));
            }
        }
        if (resolvedInterceptors == null) {
            resolvedInterceptors = List.of();
        }

        RequestPhaseContext.setPhase(Phase.INFO);
        LOGGER.debug("Found {} SSE wildcard interceptors", resolvedInterceptors.size());

        for (var ri : resolvedInterceptors) {
            try {
                RequestPhaseContext.setPhase(Phase.ITEM);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("{} (priority: {})", PluginUtils.name(ri), PluginUtils.priority(ri));
                }

                ri.handle(request, response);
            } catch (Exception ex) {
                RequestPhaseContext.setPhase(Phase.SUBITEM);
                LOGGER.error("✗ FAILED: {}", ex.getMessage());

                Exchange.setInError(exchange);
                LambdaUtils.throwsSneakyException(new InterceptorException("Error executing interceptor " + ri.getClass().getSimpleName(), ex));
            }
        }

        RequestPhaseContext.setPhase(Phase.PHASE_END);
        LOGGER.debug("{} SSE WILDCARD INTERCEPTORS COMPLETED", interceptPoint);
        RequestPhaseContext.reset();

        // Denial only takes effect after auth, mirroring RequestInterceptorsExecutor:
        // denying before auth would let an unauthenticated client learn something
        // about the endpoint from the error response.
        if (this.interceptPoint == InterceptPoint.REQUEST_AFTER_AUTH && Exchange.isInError(exchange)) {
            if (response.getStatusCode() < 0) {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            }

            exchange.setStatusCode(response.getStatusCode());

            var content = response.readContent();
            if (content != null) {
                exchange.getResponseSender().send(content);
            } else {
                exchange.endExchange();
            }
        } else {
            next(exchange);
        }
    }
}
