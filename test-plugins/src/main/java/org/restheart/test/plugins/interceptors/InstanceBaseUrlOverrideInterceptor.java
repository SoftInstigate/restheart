package org.restheart.test.plugins.interceptors;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

/**
 * Test interceptor that stands in for a multi-tenant deployment, which attaches each tenant's own
 * {@code instance-base-url} to the request (#749).
 *
 * <p>Activates only when the request carries {@code _instance-base-url-override=<url>}, attaching
 * {@code override-mongo-instance-base-url}. Every other request is untouched, so no other test is
 * affected. Used by karate/location-header.feature.
 */
@RegisterPlugin(
        name = "instanceBaseUrlOverrideInterceptor",
        description = "Attaches override-mongo-instance-base-url per-request when ?_instance-base-url-override=<url> is present",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
        priority = 20,
        enabledByDefault = false)
public class InstanceBaseUrlOverrideInterceptor implements WildcardInterceptor {

    private static final String QPARAM = "_instance-base-url-override";

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        var url = req.getExchange().getQueryParameters().get(QPARAM).peekFirst();
        if (url != null && !url.isBlank()) {
            req.attachParam("override-mongo-instance-base-url", url);
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        var params = req.getExchange().getQueryParameters();
        return params.containsKey(QPARAM) && !params.get(QPARAM).isEmpty();
    }
}
