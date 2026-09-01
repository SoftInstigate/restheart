package org.restheart.test.plugins.interceptors;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

/**
 * Test interceptor that simulates a deployment-layer interceptor (e.g.
 * restheart-cloud's {@code TeamConfigInterceptor}) attaching per-team OAuth
 * overrides — {@code override-accounts-oauth-*} — to the request.
 *
 * <p>Activates only when the request carries the query parameter
 * {@code _oauth-override=1}, attaching a fixed bundle of sentinel override
 * values distinct from the static {@code oauthConfig} test provider
 * ({@code test-client}/{@code test-secret}, {@code http://localhost:8080}).
 *
 * <p>Karate tests attach this parameter to <em>both</em> the
 * {@code /auth/oauth/authorize} and {@code /auth/oauth/callback} requests of a
 * flow — mirroring how a real per-tenant interceptor re-derives the same
 * overrides on every request to that tenant's hostname, independently on each
 * leg (it does not rely on the OAuth {@code state} to carry tenant info the
 * way {@code OAuthService} does for the database).
 *
 * <p>Used to prove {@code OAuthService} honors these overrides on the callback
 * leg, not just on authorize — see {@code RequestOverrides#oauthProvider} and
 * {@code OAuthService#handleCallback}.
 */
@RegisterPlugin(
        name = "oauthOverrideInterceptor",
        description = "Attaches override-accounts-oauth-* per-request when ?_oauth-override=1 is present",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
        priority = 20,
        enabledByDefault = false)
public class OAuthOverrideInterceptor implements WildcardInterceptor {

    private static final String QPARAM = "_oauth-override";

    public static final String OVERRIDDEN_API_BASE_URL = "http://localhost:8080/overridden";
    public static final String OVERRIDDEN_CLIENT_ID = "overridden-client-id";
    public static final String OVERRIDDEN_CLIENT_SECRET = "overridden-client-secret";
    public static final String OVERRIDDEN_SCOPE = "overridden-scope";

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        req.attachParam("override-accounts-oauth-api-base-url", OVERRIDDEN_API_BASE_URL);
        req.attachParam("override-accounts-oauth-test-client-id", OVERRIDDEN_CLIENT_ID);
        req.attachParam("override-accounts-oauth-test-client-secret", OVERRIDDEN_CLIENT_SECRET);
        req.attachParam("override-accounts-oauth-test-scope", OVERRIDDEN_SCOPE);
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        var params = req.getExchange().getQueryParameters();
        return params.containsKey(QPARAM) && !params.get(QPARAM).isEmpty();
    }
}
