package org.restheart.security.plugins.interceptors;

import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.plugins.authorizers.AclPermission;
import org.restheart.utils.JsonUtils;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonElement;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonNumber;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.idm.MongoRealmAccount;
import org.restheart.plugins.InterceptPoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.attribute.ExchangeAttributes;

@RegisterPlugin(name = "mongoPermissionsOverriddenProps", description = "Override properties's values in write requests according to the mongo.overriddenProps ACL permission", interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH, enabledByDefault = true)
public class MongoPermissionsOverriddenProps implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoPermissionsOverriddenProps.class);

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var overriddenProps = AclPermission.from(request.getExchange()).getMongoPermissions().getOverriddenProps();

        if (request.getContent().isDocument()) {
            override(request, overriddenProps);
        } else if (request.getContent().isArray()) {
            request.getContent().asArray().stream().map(doc -> doc.asDocument())
                    .forEachOrdered(doc -> override(request, overriddenProps));
        }

        if (request.isPost()) {
            request.setContent(JsonUtils.unflatten(request.getContent()));
        }
    }

    private void override(MongoRequest request, Map<String, BsonValue> overriddenProps) {
        overriddenProps.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().isString())
                .forEachOrdered(e -> override(request, e.getKey(), e.getValue().asString().getValue()));
    }

    private void override(MongoRequest request, String key, String value) {
        request.getContent().asDocument().put(key, interpolateValue(request, key, value));
    }

    private BsonValue interpolateValue(MongoRequest request, String key, String value) {
        if (value == null) {
            return BsonNull.VALUE;
        } else if ("%USER".equals(value) || "@user.id".equals(value)) {
            return new BsonString(ExchangeAttributes.remoteUser().readAttribute(request.getExchange()));
        } else if ("%ROLES".equals(value) || "@user.roles".equals(value)) {
            if (Objects.nonNull(request.getAuthenticatedAccount())
                    && Objects.nonNull(request.getAuthenticatedAccount().getRoles())) {
                var ret = new BsonArray();
                request.getAuthenticatedAccount().getRoles().stream().map(s -> new BsonString(s))
                        .forEachOrdered(ret::add);
                return ret;
            } else {
                return new BsonArray();
            }
        } else if ("%NOW".equals(value) || "@now".equals(value)) {
            return new BsonDateTime(Instant.now().getEpochSecond() * 1000);
        } else if (value.startsWith("@user.")) {
            if (request.getAuthenticatedAccount() instanceof MongoRealmAccount) {
                var maccount = (MongoRealmAccount) request.getAuthenticatedAccount();
                var accountDoc = maccount.getAccountDocument();
                var prop = value.substring(6);

                LOGGER.debug("account doc: {}", accountDoc.toJson());

                if (prop.contains(".")) {
                    try {
                        JsonElement v = JsonPath.read(accountDoc.toJson(), "$.".concat(prop));

                        return JsonUtils.parse(v.toString());
                    } catch (Throwable pnfe) {
                        return BsonNull.VALUE;
                    }
                } else {
                    if (accountDoc.containsKey(prop)) {
                        return accountDoc.get(prop);
                    } else {
                        return BsonNull.VALUE;
                    }
                }
            } else {
                return BsonNull.VALUE;
            }
        } else {
            return new BsonString(value);
        }
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
