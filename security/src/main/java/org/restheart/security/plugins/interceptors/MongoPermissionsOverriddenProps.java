package org.restheart.security.plugins.interceptors;

import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.plugins.authorizers.AclPermission;
import org.restheart.utils.JsonUtils;



import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(name = "mongoPermissionsOverriddenProps",
    description = "Override properties's values in write requests according to the mongo.overriddenProps ACL permission",
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
    enabledByDefault = true)
public class MongoPermissionsOverriddenProps implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoPermissionsOverriddenProps.class);

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var overriddenProps = AclPermission.from(request.getExchange()).getMongoPermissions().getOverriddenProps();

        if (request.getContent().isDocument()) {
            override(request.getContent().asDocument(), overriddenProps);
        } else if (request.getContent().isArray()) {
            request.getContent().asArray().stream().map(doc -> doc.asDocument()).forEachOrdered(doc -> override(doc, overriddenProps));
        }

        if (request.isPost()) {
            request.setContent(JsonUtils.unflatten(request.getContent()));
        }
    }

    private void override(BsonDocument doc, Map<String, BsonValue> overriddenProps) {
        overriddenProps.entrySet().stream().forEachOrdered(e -> override(doc, e.getKey(), e.getValue()));
    }

    private void override(BsonDocument doc, String key, BsonValue value) {
        doc.put(key, interpolateValue(value));
    }

    private BsonValue interpolateValue(BsonValue value) {
        // TODO interpolate values
        return value;
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        if (!request.isHandledBy("mongo") || request.getContent() == null) {
            return false;
        }

        var permission = AclPermission.from(request.getExchange());

        if (permission != null && permission.getMongoPermissions() != null) {
            return !permission.getMongoPermissions().getProtectedProps().isEmpty();
        } else {
            return false;
        }
    }
}
