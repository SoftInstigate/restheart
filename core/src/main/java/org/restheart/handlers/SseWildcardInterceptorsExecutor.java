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
import io.undertow.util.AttachmentKey;

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
 * <p>The SSE pipeline runs this executor twice for the same exchange: once at
 * {@code REQUEST_BEFORE_AUTH}, once at {@code REQUEST_AFTER_AUTH}. A denial raised via
 * {@code response.setInError(...)} at {@code REQUEST_BEFORE_AUTH} is <strong>deferred, not
 * ignored</strong>, mirroring {@link RequestInterceptorsExecutor} (see the comment above its
 * {@code REQUEST_AFTER_AUTH} check): sending the error response before authentication would
 * let an unauthenticated client learn something about the endpoint. The two invocations share
 * a single {@code SseHandshakeRequest}/{@code SseHandshakeResponse} pair for the exchange,
 * attached under an {@link AttachmentKey} owned by this class (deliberately not
 * {@code ServiceRequest}/{@code ServiceResponse}'s own {@code REQUEST_KEY}/{@code RESPONSE_KEY},
 * which other handlers on this pipeline rely on staying unpopulated): whichever invocation runs
 * first creates the pair, the other reuses it. So a status code and body set on a
 * {@code REQUEST_BEFORE_AUTH} denial are still the ones sent once {@code REQUEST_AFTER_AUTH}
 * observes the exchange in error.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 * @see WildcardInterceptor
 * @see SseHandshakeRequest
 * @see SseHandshakeResponse
 */
public class SseWildcardInterceptorsExecutor extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SseWildcardInterceptorsExecutor.class);

    /**
     * Attachment key under which the {@code SseHandshakeRequest} shared by the
     * {@code REQUEST_BEFORE_AUTH} and {@code REQUEST_AFTER_AUTH} invocations of this executor
     * is stored, so a denial raised before auth carries its request context to the invocation
     * that sends it.
     */
    private static final AttachmentKey<SseHandshakeRequest> SSE_HANDSHAKE_REQUEST_KEY = AttachmentKey.create(SseHandshakeRequest.class);

    /**
     * Attachment key under which the {@code SseHandshakeResponse} shared by the
     * {@code REQUEST_BEFORE_AUTH} and {@code REQUEST_AFTER_AUTH} invocations of this executor
     * is stored, so a denial raised before auth carries its status code and body to the
     * invocation that sends it.
     */
    private static final AttachmentKey<SseHandshakeResponse> SSE_HANDSHAKE_RESPONSE_KEY = AttachmentKey.create(SseHandshakeResponse.class);

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
        var request = exchange.getAttachment(SSE_HANDSHAKE_REQUEST_KEY);
        if (request == null) {
            request = SseHandshakeRequest.of(exchange);
            exchange.putAttachment(SSE_HANDSHAKE_REQUEST_KEY, request);
        }

        var response = exchange.getAttachment(SSE_HANDSHAKE_RESPONSE_KEY);
        if (response == null) {
            response = SseHandshakeResponse.of(exchange);
            exchange.putAttachment(SSE_HANDSHAKE_RESPONSE_KEY, response);
        }

        if (this.wildcardInterceptors.isEmpty()) {
            // nothing of this executor's own to resolve/run, but a pending denial from the
            // other invocation of this executor on the same exchange (see the shared
            // request/response pair above) must still reach the terminal check below:
            // an empty list here must never bypass it.
            LOGGER.debug("{} SSE WILDCARD INTERCEPTORS: none registered", interceptPoint);
        } else {
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
        }

        // Denial only takes effect after auth, mirroring RequestInterceptorsExecutor:
        // denying before auth would let an unauthenticated client learn something
        // about the endpoint from the error response. Reached unconditionally (not just
        // when this executor has interceptors of its own): a denial raised by the
        // REQUEST_BEFORE_AUTH invocation of this executor must still be observed and sent
        // here even if zero WildcardInterceptors are registered for REQUEST_AFTER_AUTH.
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
