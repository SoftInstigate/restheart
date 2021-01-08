package org.restheart.security.plugins.interceptors;

import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.plugins.authorizers.AclPermission;
import org.restheart.utils.HttpStatus;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.ExchangeKeys.WRITE_MODE;
import org.restheart.plugins.InterceptPoint;

@RegisterPlugin(name = "mongoPermissionsAllowAllWriteModes",
    description = "Allow clients to specify the write mode according to the mongo.allowAllWriteModes ACL permission (otherwise POST only inserts, PUT and PATCH only update)",
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
    enabledByDefault = true)
public class MongoPermissionsAllowAllWriteModes implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        if ((request.isPatch() || request.isDelete()) && request.isBulkDocuments()) {
            response.setStatusCode(HttpStatus.SC_FORBIDDEN);
            response.setInError(true);
            return;
        }

        if (request.isPost()) {
            request.setWriteMode(WRITE_MODE.INSERT);
        } else if (request.isPatch() || request.isPut()) {
            request.setWriteMode(WRITE_MODE.UPDATE);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        if (!request.isHandledBy("mongo") || !request.isWriteDocument()) {
            return false;
        }

        var permission = AclPermission.from(request.getExchange());

        if (permission != null && permission.getMongoPermissions() != null) {
            return !permission.getMongoPermissions().isAllowAllWriteModes();
        } else {
            return false;
        }
    }
}
