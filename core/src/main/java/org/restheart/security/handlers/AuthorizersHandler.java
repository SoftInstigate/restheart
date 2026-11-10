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

import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.exchange.Exchange;
import org.restheart.exchange.Request;
import org.restheart.handlers.CORSHandler;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.RequestInterceptorsExecutor;
import org.restheart.logging.RequestPhaseContext;
import org.restheart.logging.RequestPhaseContext.Phase;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Service;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.Authorizer.TYPE;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * Executes isAllowed() on all enabled authorizer to check the request
 * An Authorizer can be either a VETOER or an ALLOWER
 * A request is allowed when no VETOER denies it and any ALLOWER allows it
 *
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthorizersHandler extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizersHandler.class);

    // split once by type, in the order of the authorizers set
    private final Entry[] vetoers;
    private final Entry[] allowers;
    private final RequestInterceptorsExecutor failedAuthInterceptorsExecutor;

    /**
     * Creates a new instance of AuthorizersHandler
     *
     * @param authorizers
     * @param next
     */
    public AuthorizersHandler(Set<PluginRecord<Authorizer>> authorizers, PipelinedHandler next) {
        super(next);
        this.vetoers = entries(authorizers, TYPE.VETOER);
        this.allowers = entries(authorizers, TYPE.ALLOWER);
        this.failedAuthInterceptorsExecutor = new RequestInterceptorsExecutor(InterceptPoint.REQUEST_AFTER_FAILED_AUTH);
    }

    /**
     * Creates a new instance of AuthorizersHandler
     *
     * @param authorizers
     * @param service the service handling these requests, kept for the callers that name it;
     *                authorization itself reads only the request
     * @param next
     */
    public AuthorizersHandler(Set<PluginRecord<Authorizer>> authorizers, Service<?, ?> service, PipelinedHandler next) {
        this(authorizers, next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = Request.of(exchange);
        final var debug = LOGGER.isDebugEnabled();
        final var authorizationStartTime = debug ? System.currentTimeMillis() : 0L;

        if (debug) {
            RequestPhaseContext.setPhase(Phase.PHASE_START);
            LOGGER.debug("AUTHORIZATION for {} {} - User: {}", exchange.getRequestMethod(), exchange.getRequestPath(), principal(request));
        }

        var isAllowedResult = isAllowed(request);
        var authorizationDuration = debug ? System.currentTimeMillis() - authorizationStartTime : 0L;

        if (isAllowedResult) {
            RequestPhaseContext.setPhase(Phase.PHASE_END);
            LOGGER.debug("✓ ACCESS GRANTED ({}ms)", authorizationDuration);
            RequestPhaseContext.reset();
            next(exchange);
        } else {
            RequestPhaseContext.setPhase(Phase.PHASE_END);
            LOGGER.debug("✗ ACCESS DENIED → 403 Forbidden ({}ms)", authorizationDuration);
            RequestPhaseContext.reset();

            // execute REQUEST_AFTER_FAILED_AUTH interceptors
            failedAuthInterceptorsExecutor.handleRequest(exchange);

            // add CORS headers
            CORSHandler.injectAccessControlAllowHeaders(exchange);
            // set status code and end exchange
            exchange.setStatusCode(HttpStatus.SC_FORBIDDEN);

            // an authorizer (e.g. a VETOER) can attach a specific denial reason via
            // request.attachParam(Authorizer.VETO_MESSAGE, "..."); when present, return
            // it as the response body instead of an empty 403
            // the reason for whoever writes the permissions, in the log only
            final String vetoLogMessage = (String) request.attachedParam(Authorizer.VETO_LOG_MESSAGE);
            if (vetoLogMessage != null) {
                LOGGER.info("403 for {} {}: {}", exchange.getRequestMethod(), exchange.getRequestPath(), vetoLogMessage);
            }

            final String vetoMessage = (String) request.attachedParam(Authorizer.VETO_MESSAGE);
            if (vetoMessage != null) {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, Exchange.JSON_MEDIA_TYPE);
                exchange.getResponseSender().send(BsonUtils.toJson(new BsonDocument("message", new BsonString(vetoMessage))));
            }

            exchange.endExchange();
        }
    }

    /**
     *
     * @param request
     * @return true if no vetoer vetoes the request and any allower allows it
     */
    private boolean isAllowed(final Request<?> request) {
        final var debug = LOGGER.isDebugEnabled();

        if (vetoers.length == 0 && allowers.length == 0) {
            if (debug) {
                LOGGER.debug("No authorizers configured for {} {} - User: {} - Access DENIED",
                        request.getMethod(), request.getPath(), principal(request));
            }
            return false;
        }

        // Check VETOER authorizers first
        for (var vetoer : vetoers) {
            if (!vetoer.applies(request)) {
                continue;
            }

            final var startTime = debug ? System.currentTimeMillis() : 0L;

            try {
                final var allowed = vetoer.authorizer.isAllowed(request);

                if (debug) {
                    RequestPhaseContext.setPhase(Phase.ITEM);
                    LOGGER.debug("VETOER {}: {} ({}ms)", vetoer.name, allowed ? "✓" : "✗", System.currentTimeMillis() - startTime);
                }

                if (!allowed) {
                    return false;
                }
            } catch (Exception ex) {
                LOGGER.error("Error in VETOER {} ({}) for {} {} - User: {}",
                        vetoer.name, vetoer.authorizer.getClass().getSimpleName(), request.getMethod(), request.getPath(), principal(request), ex);
                return false;
            }
        }

        // Check ALLOWER authorizers
        for (var allower : allowers) {
            if (!allower.applies(request)) {
                continue;
            }

            final var startTime = debug ? System.currentTimeMillis() : 0L;

            try {
                final var allowed = allower.authorizer.isAllowed(request);

                if (debug) {
                    RequestPhaseContext.setPhase(Phase.ITEM);
                    LOGGER.debug("ALLOWER {}: {} ({}ms)", allower.name, allowed ? "✓" : "✗", System.currentTimeMillis() - startTime);
                }

                if (allowed) {
                    return true;
                }
            } catch (Exception ex) {
                LOGGER.error("Error in ALLOWER {} ({}) for {} {} - User: {}",
                        allower.name, allower.authorizer.getClass().getSimpleName(), request.getMethod(), request.getPath(), principal(request), ex);
            }
        }

        return false;
    }

    private static String principal(Request<?> request) {
        return request.isAuthenticated() ? request.getAuthenticatedAccount().getPrincipal().getName() : "anonymous";
    }

    /**
     * An authorizer with the metadata read once, at construction, instead of on every request.
     */
    @SuppressWarnings("rawtypes")
    private record Entry(PluginRecord<Authorizer> record, Authorizer authorizer, String name) {
        @SuppressWarnings("unchecked")
        boolean applies(Request<?> request) {
            return record.isEnabled() && (!authorizer.isAuthenticationRequired(request) || request.isAuthenticated());
        }
    }

    private static Entry[] entries(Set<PluginRecord<Authorizer>> authorizers, TYPE type) {
        if (authorizers == null) {
            return new Entry[0];
        }

        return authorizers.stream()
                .filter(a -> a.getInstance() != null)
                .filter(a -> PluginUtils.authorizerType(a.getInstance()) == type)
                .map(a -> new Entry(a, a.getInstance(), PluginUtils.name(a.getInstance())))
                .toArray(Entry[]::new);
    }
}
