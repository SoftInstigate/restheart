package org.restheart.security.plugins.interceptors;

import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.plugins.authorizers.AclPermission;
import org.restheart.utils.HttpStatus;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;

@RegisterPlugin(name = "mongoPermissionsForbidMgmgRequests", 
    description = "Whitelists mongo management requests according to the mongo.whitelistManagementRequests ACL permission", 
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH, 
    enabledByDefault = true)
public class MongoPermissionsWhitelistMgmgRequests implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        if ((request.isDb() && !request.isGet()) || // create/delete dbs
                (request.isCollection() && (!request.isGet() && !request.isPost())) || // create/delete collections

                (request.isIndex()) || // indexes
                (request.isCollectionIndexes()) || // indexes

                (request.isFilesBucket() && !request.isGet()) || // create/delete file buckets

                (request.isSchema()) || // schema store
                (request.isSchemaStore()) || // schema store
                (request.isSchemaStoreSize()) || // schema store size

                (request.isDbMeta()) || // db metadata
                (request.isCollectionMeta()) || // collection metadata
                (request.isFilesBucketMeta()) || // file bucket metadata
                (request.isSchemaStoreMeta()) // schema store metadata
        ) {
            response.setStatusCode(HttpStatus.SC_FORBIDDEN);
            response.setInError(true);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        var permission = AclPermission.from(request.getExchange());

        if (!request.isHandledBy("mongo")
            || permission == null
            || permission.getMongoPermissions() == null) {
            return false;
        }

        return !permission.getMongoPermissions().isWhitelistManagementRequests();
    }
}
