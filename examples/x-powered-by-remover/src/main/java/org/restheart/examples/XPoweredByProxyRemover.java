package org.restheart.examples;

import org.restheart.plugins.RegisterPlugin;
import org.restheart.exchange.ByteArrayProxyRequest;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.ProxyInterceptor;

import com.google.common.net.HttpHeaders;

@RegisterPlugin(name = "xPoweredByProxyRemover",
    interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH,
    description = "removes the X-Powered-By response from proxied responses")
public class XPoweredByProxyRemover implements ProxyInterceptor {
    @Override
    public void handle(ByteArrayProxyRequest request, ByteArrayProxyResponse response) throws Exception {
        response.getHeaders().remove(HttpHeaders.X_POWERED_BY);
    }

    @Override
    public boolean resolve(ByteArrayProxyRequest request, ByteArrayProxyResponse response) {
        return true;
    }
}