package org.restheart.test.plugins.interceptors;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

import java.util.Arrays;

/**
 * Test interceptor that simulates {@code TeamConfigInterceptor} attaching
 * {@code override-accounts-account-properties-claims} for specific requests.
 *
 * <p>Activates only when the request carries the query parameter
 * {@code _claims-override=<comma-separated-claim-list>}, attaching the parsed list
 * as {@code override-accounts-account-properties-claims}.
 *
 * <p>Used by Karate tests to verify that {@code JwtHelper} resolves the effective
 * claims list per-request via {@code RequestOverrides.accountPropertiesClaims(req, conf)},
 * and that its denylist cannot be bypassed by the override, without affecting any other
 * test (those simply don't include the query param).
 *
 * <p>Example Karate call:
 * <pre>
 *   Given url baseUrl + '/auth/verify?_claims-override=profile,password'
 * </pre>
 */
@RegisterPlugin(
        name = "accountPropertiesClaimsOverrideInterceptor",
        description = "Attaches override-accounts-account-properties-claims per-request when ?_claims-override=<a,b,c> is present",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
        priority = 20,
        enabledByDefault = false)
public class AccountPropertiesClaimsOverrideInterceptor implements WildcardInterceptor {

    private static final String QPARAM = "_claims-override";

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        var value = req.getExchange().getQueryParameters().get(QPARAM).peekFirst();
        if (value != null && !value.isBlank()) {
            req.attachParam("override-accounts-account-properties-claims", Arrays.asList(value.split(",")));
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        var params = req.getExchange().getQueryParameters();
        return params.containsKey(QPARAM) && !params.get(QPARAM).isEmpty();
    }
}
