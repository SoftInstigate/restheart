package org.restheart.examples;

import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import com.google.common.net.HttpHeaders;

@RegisterPlugin(name = "xPoweredByRemover",
    interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH,
    description = "removes the X-Powered-By header from service resposes")
public class XPoweredByRemover implements WildcardInterceptor {
    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) throws Exception {
        response.getHeaders().remove(HttpHeaders.X_POWERED_BY);
    }

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        return true;
    }
}