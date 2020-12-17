package org.restheart.mongodb.interceptors;

import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.ExchangeKeys.WRITE_MODE;
import org.restheart.plugins.InterceptPoint;

@RegisterPlugin(name = "simplifiedSecurityWriteMode",
    description = "Restricts the write requests to allow POST to only insert new documents and PUT and PATCH to only update existing documents; this greatly simplifies the definition of security permissions",
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
    enabledByDefault = false)
public class SimplifiedSecurityWriteMode implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        if (request.isPost()) {
            request.setWriteMode(WRITE_MODE.INSERT);
        } else if (request.isPatch() || request.isPut()) {
            request.setWriteMode(WRITE_MODE.UPDATE);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo") && request.isWriteDocument();
    }
}
