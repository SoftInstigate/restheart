package org.restheart.examples;

import java.util.HashMap;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.MDC;

@RegisterPlugin(name = "customLoggingFieldInteceptor",
    interceptPoint = InterceptPoint.RESPONSE,
    description = "add a custom logging filed to log messages")
public class CustomLoggingFieldInteceptor implements WildcardInterceptor {
    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) throws Exception {
        // the MDC Context can be initialized by other plugins
        var mdcCtx = response.getMDCContext();

        // it migth be initialized by other plugins
        if (mdcCtx == null) {
            var _mdcCtx = MDC.getCopyOfContextMap() == null ? new HashMap<String, String>(): MDC.getCopyOfContextMap();
            response.setMDCContext(_mdcCtx);
            mdcCtx = _mdcCtx;
        }

        mdcCtx.put("foo", "bar");
    }

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        return true;
    }
}