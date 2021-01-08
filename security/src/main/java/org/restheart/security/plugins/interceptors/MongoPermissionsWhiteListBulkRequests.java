package org.restheart.security.plugins.interceptors;

import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.plugins.authorizers.AclPermission;
import org.restheart.utils.HttpStatus;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;

@RegisterPlugin(name = "mongoPermissionsWitheListBulkRequests",
    description = "Whitelists bulk PATCH and bulk DELETE according to the mongo.whitelistBulkPatch and mongo.whitelistBulkDelete ACL permissions",
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
    enabledByDefault = true)
public class MongoPermissionsWhiteListBulkRequests implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        response.setStatusCode(HttpStatus.SC_FORBIDDEN);
        response.setInError(true);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        var permission = AclPermission.from(request.getExchange());

        if (!request.isHandledBy("mongo")
            || permission == null
            || permission.getMongoPermissions() == null) {
            return false;
        }

        return (!permission.getMongoPermissions().isWhitelistBulkDelete() && request.isBulkDocuments() && request.isDelete())
            || (!permission.getMongoPermissions().isWhitelistBulkPatch() && request.isBulkDocuments() && request.isPatch());
    }
}
