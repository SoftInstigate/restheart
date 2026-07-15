package org.restheart.test.plugins.interceptors;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

/**
 * Test interceptor that simulates {@code AuthDbResolver} for specific requests.
 *
 * <p>Activates only when the request carries the query parameter
 * {@code _db-override=<dbName>}, attaching {@code override-users-db}
 * and {@code override-acl-db} to the request.
 *
 * <p>Used by Karate tests to verify that {@code DefaultMembershipProvider}
 * resolves the database per-request via {@code RequestOverrides.db(req, conf)},
 * without affecting any other test (those simply don't include the query param).
 *
 * <p>Example Karate call:
 * <pre>
 *   Given url baseUrl + '/auth/register?_db-override=restheart-test'
 * </pre>
 */
@RegisterPlugin(
    name = "dbOverrideInterceptor",
    description = "Attaches override-users-db per-request when ?_db-override=<db> is present",
    interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
    priority = 20,
    enabledByDefault = false)
public class DbOverrideInterceptor implements WildcardInterceptor {

    private static final String QPARAM = "_db-override";

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        var db = req.getExchange().getQueryParameters().get(QPARAM).peekFirst();
        if (db != null && !db.isBlank()) {
            req.attachParam("override-users-db", db);
            req.attachParam("override-acl-db", db);
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        var params = req.getExchange().getQueryParameters();
        return params.containsKey(QPARAM) && !params.get(QPARAM).isEmpty();
    }
}
