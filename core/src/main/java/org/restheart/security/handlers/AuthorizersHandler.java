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

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.restheart.plugins.security.DescriptorAwareAuthorizer;
import org.restheart.plugins.security.RequestDescriptor;
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
 * <p>Additionally — see restheart#722 — if the {@link Service} handling this request overrides
 * {@link Service#operationsToAuthorize(HttpServerExchange)}, each {@link RequestDescriptor} it
 * returns is independently checked against every registered {@link DescriptorAwareAuthorizer}
 * with the same VETOER/ALLOWER semantics; a service that doesn't override it (the vast majority
 * — every ordinary REST service) never triggers this additional check at all. If no
 * {@link DescriptorAwareAuthorizer} is configured, a service that does override it fails closed
 * (denied) rather than allowing an operation no authorizer actually evaluated.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthorizersHandler extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizersHandler.class);

    private final Set<PluginRecord<Authorizer>> authorizers;
    private final Service<?, ?> service;
    private final RequestInterceptorsExecutor failedAuthInterceptorsExecutor;

    /**
     * Creates a new instance of AuthorizersHandler
     *
     * @param authorizers
     * @param next
     */
    public AuthorizersHandler(Set<PluginRecord<Authorizer>> authorizers, PipelinedHandler next) {
        this(authorizers, null, next);
    }

    /**
     * Creates a new instance of AuthorizersHandler
     *
     * @param authorizers
     * @param service the {@link Service} handling requests routed through this handler, or
     *                {@code null} when not applicable (e.g. an SSE service, which never overrides
     *                {@code operationsToAuthorize}) — see restheart#722
     * @param next
     */
    public AuthorizersHandler(Set<PluginRecord<Authorizer>> authorizers, Service<?, ?> service, PipelinedHandler next) {
        super(next);
        this.authorizers = authorizers;
        this.service = service;
        this.failedAuthInterceptorsExecutor = new RequestInterceptorsExecutor(InterceptPoint.REQUEST_AFTER_FAILED_AUTH);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = Request.of(exchange);
        var requestPath = exchange.getRequestPath();
        var requestMethod = exchange.getRequestMethod().toString();
        var authorizationStartTime = System.currentTimeMillis();
        var isAuthenticated = request.isAuthenticated();
        var userPrincipal = isAuthenticated ? request.getAuthenticatedAccount().getPrincipal().getName() : "anonymous";

        RequestPhaseContext.setPhase(Phase.PHASE_START);
        LOGGER.debug("AUTHORIZATION for {} {} - User: {}", requestMethod, requestPath, userPrincipal);

        var isAllowedResult = isAllowed(request) && isAllowedByDescriptors(exchange);
        var authorizationDuration = System.currentTimeMillis() - authorizationStartTime;

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
    @SuppressWarnings("rawtypes")
    private boolean isAllowed(final Request request) {
        var requestPath = request.getPath();
        var requestMethod = request.getMethod().toString();
        var isAuthenticated = request.isAuthenticated();
        var userPrincipal = isAuthenticated ? request.getAuthenticatedAccount().getPrincipal().getName() : "anonymous";

        if (authorizers == null || authorizers.isEmpty()) {
            LOGGER.debug("No authorizers configured for {} {} - User: {} - Access DENIED",
                    requestMethod, requestPath, userPrincipal);
            return false;
        }

        // Check VETOER authorizers first
        var vetoers = authorizers.stream()
                .filter(a -> a.isEnabled())
                .filter(a -> a.getInstance() != null)
                .map(a -> a.getInstance())
                .filter(a -> !a.isAuthenticationRequired(request) || request.isAuthenticated())
                .filter(a -> PluginUtils.authorizerType(a) == TYPE.VETOER)
                .collect(Collectors.toList());

        if (!vetoers.isEmpty()) {
            RequestPhaseContext.setPhase(Phase.INFO);
            LOGGER.debug("Checking {} VETOER authorizers", vetoers.size());
        }

        var vetoerResult = true;

        for (var vetoer : vetoers) {
            var vetoerName = PluginUtils.name(vetoer);
            var vetoerClass = vetoer.getClass().getSimpleName();
            var vetoerCheckStartTime = System.currentTimeMillis();

            try {
                var allowed = vetoer.isAllowed(request);
                var vetoerCheckDuration = System.currentTimeMillis() - vetoerCheckStartTime;

                RequestPhaseContext.setPhase(Phase.ITEM);
                LOGGER.debug("VETOER {}: {} ({}ms)", vetoerName, allowed ? "✓" : "✗", vetoerCheckDuration);

                if (!allowed) {
                    vetoerResult = false;
                    break;
                }
            } catch (Exception ex) {
                var vetoerCheckDuration = System.currentTimeMillis() - vetoerCheckStartTime;
                LOGGER.error("Error in VETOER {} ({}) for {} {} - User: {} after {}ms",
                        vetoerName, vetoerClass, requestMethod, requestPath, userPrincipal, vetoerCheckDuration, ex);
                vetoerResult = false;
                break;
            }
        }

        if (!vetoerResult) {
            return false;
        }

        // Check ALLOWER authorizers
        var allowers = authorizers.stream()
                .filter(a -> a.isEnabled())
                .filter(a -> a.getInstance() != null)
                .map(a -> a.getInstance())
                .filter(a -> PluginUtils.authorizerType(a) == TYPE.ALLOWER)
                .filter(a -> !a.isAuthenticationRequired(request) || request.isAuthenticated())
                .collect(Collectors.toList());

        if (!allowers.isEmpty()) {
            RequestPhaseContext.setPhase(Phase.INFO);
            LOGGER.debug("Checking {} ALLOWER authorizers", allowers.size());
        }

        var allowerResult = false;

        for (var allower : allowers) {
            var allowerName = PluginUtils.name(allower);
            var allowerClass = allower.getClass().getSimpleName();
            var allowerCheckStartTime = System.currentTimeMillis();

            try {
                var allowed = allower.isAllowed(request);
                var allowerCheckDuration = System.currentTimeMillis() - allowerCheckStartTime;

                RequestPhaseContext.setPhase(Phase.ITEM);
                LOGGER.debug("ALLOWER {}: {} ({}ms)", allowerName, allowed ? "✓" : "✗", allowerCheckDuration);

                if (allowed) {
                    allowerResult = true;
                    break;
                }
            } catch (Exception ex) {
                var allowerCheckDuration = System.currentTimeMillis() - allowerCheckStartTime;
                LOGGER.error("Error in ALLOWER {} ({}) for {} {} - User: {} after {}ms",
                        allowerName, allowerClass, requestMethod, requestPath, userPrincipal, allowerCheckDuration, ex);
            }
        }

        return vetoerResult && allowerResult;
    }

    /**
     * See restheart#722. A no-op (always {@code true}) unless {@link #service} actually overrides
     * {@link Service#operationsToAuthorize(HttpServerExchange)} — an ordinary REST service never
     * pays for or triggers this check at all.
     */
    private boolean isAllowedByDescriptors(HttpServerExchange exchange) {
        if (service == null || !overridesOperationsToAuthorize(service)) {
            return true;
        }

        var descriptors = service.operationsToAuthorize(exchange);

        var descriptorAuthorizers = authorizers.stream()
                .filter(PluginRecord::isEnabled)
                .map(PluginRecord::getInstance)
                .filter(DescriptorAwareAuthorizer.class::isInstance)
                .map(DescriptorAwareAuthorizer.class::cast)
                .toList();

        if (descriptorAuthorizers.isEmpty()) {
            LOGGER.debug("No DescriptorAwareAuthorizer configured — denying {} operation(s) to authorize for service '{}'",
                    descriptors.size(), PluginUtils.name(service));
            return false;
        }

        var vetoers = descriptorAuthorizers.stream().filter(a -> PluginUtils.authorizerType(a) == TYPE.VETOER).toList();
        var allowers = descriptorAuthorizers.stream().filter(a -> PluginUtils.authorizerType(a) == TYPE.ALLOWER).toList();

        for (var descriptor : descriptors) {
            for (var vetoer : vetoers) {
                try {
                    if (!vetoer.isAllowed(descriptor)) {
                        return false;
                    }
                } catch (Exception ex) {
                    LOGGER.error("Error in VETOER {} evaluating a RequestDescriptor for service '{}'",
                            PluginUtils.name(vetoer), PluginUtils.name(service), ex);
                    return false;
                }
            }

            var descriptorAllowed = false;
            for (var allower : allowers) {
                try {
                    if (allower.isAllowed(descriptor)) {
                        descriptorAllowed = true;
                        break;
                    }
                } catch (Exception ex) {
                    LOGGER.error("Error in ALLOWER {} evaluating a RequestDescriptor for service '{}'",
                            PluginUtils.name(allower), PluginUtils.name(service), ex);
                }
            }

            if (!descriptorAllowed) {
                return false;
            }
        }

        return true;
    }

    /**
     * Package-visible for testability.
     * @return {@code true} if {@code service} overrides the default (identity) {@code operationsToAuthorize()}.
     */
    static boolean overridesOperationsToAuthorize(Service<?, ?> service) {
        try {
            Method m = service.getClass().getMethod("operationsToAuthorize", HttpServerExchange.class);
            return m.getDeclaringClass() != Service.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
