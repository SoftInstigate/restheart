package org.restheart.test.plugins.interceptors;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

/**
 * Test interceptor that stands in for a tenant resolver scoping API keys.
 *
 * <p>Activates only when the request carries the query parameter
 * {@code _keys-db-override=<dbName>}, attaching {@code override-keys-db}.
 *
 * <p>Separate from {@link DbOverrideInterceptor} on purpose: that one also moves
 * the ACL database, and the API key tests need the ACL to stay where the rest of
 * the suite keeps it, so that a refusal can only come from the key lookup.
 *
 * <p>Used by {@code api-key-auth.feature} to show that a key works only on the
 * tenant whose database holds it (#738).
 */
@RegisterPlugin(
        name = "keysDbOverrideInterceptor",
        description = "Attaches override-keys-db per-request when ?_keys-db-override=<db> is present",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
        priority = 20,
        enabledByDefault = false)
public class KeysDbOverrideInterceptor implements WildcardInterceptor {

    private static final String QPARAM = "_keys-db-override";

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        var db = req.getExchange().getQueryParameters().get(QPARAM).peekFirst();
        if (db != null && !db.isBlank()) {
            req.attachParam("override-keys-db", db);
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        var params = req.getExchange().getQueryParameters();
        return params.containsKey(QPARAM) && !params.get(QPARAM).isEmpty();
    }
}
