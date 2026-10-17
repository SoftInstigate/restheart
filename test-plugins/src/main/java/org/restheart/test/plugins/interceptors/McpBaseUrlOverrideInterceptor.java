package org.restheart.test.plugins.interceptors;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

/**
 * Test interceptor that stands in for the Cloud deployment layer, which attaches the calling
 * service's own base URL to every request before {@code mcpService} sees it.
 *
 * <p>Activates only when the request carries {@code _mcp-baseurl-override=<baseUrl>}, attaching
 * {@code override-ai-mcp-public-base-url}. Every other request is untouched, so no other test is
 * affected.
 *
 * <p>On Cloud Shared that base URL is derived from the hostname, one per service. Here it is a
 * query parameter for the same reason the other {@code *OverrideInterceptor}s in this package use
 * one: the integration suite runs a single RESTHeart on a single hostname, and a host-parametric
 * mount would change routing for all 582 scenarios.
 *
 * <p>Used by {@code McpScopeIT}.
 */
@RegisterPlugin(
        name = "mcpBaseUrlOverrideInterceptor",
        description = "Attaches override-ai-mcp-public-base-url per-request when ?_mcp-baseurl-override=<url> is present",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
        priority = 20,
        enabledByDefault = false)
public class McpBaseUrlOverrideInterceptor implements WildcardInterceptor {

    private static final String QPARAM = "_mcp-baseurl-override";

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        var baseUrl = req.getExchange().getQueryParameters().get(QPARAM).peekFirst();
        if (baseUrl != null && !baseUrl.isBlank()) {
            req.attachParam("override-ai-mcp-public-base-url", baseUrl);
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        var params = req.getExchange().getQueryParameters();
        return params.containsKey(QPARAM) && !params.get(QPARAM).isEmpty();
    }
}
