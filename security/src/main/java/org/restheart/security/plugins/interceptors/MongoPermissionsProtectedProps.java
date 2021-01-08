package org.restheart.security.plugins.interceptors;

import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.plugins.authorizers.AclPermission;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.JsonUtils;

import java.util.Set;
import java.util.stream.Collectors;

import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;

@RegisterPlugin(name = "mongoPermissionsProtectedProps",
    description = "Forbids writing properties according to the mongo.protectedProps ACL permission",
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
    enabledByDefault = true)
public class MongoPermissionsProtectedProps implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var protectedProps = AclPermission.from(request.getExchange()).getMongoPermissions().getProtectedProps();

        boolean contains;

        if (request.getContent().isDocument()) {
            contains = contains(request.getContent().asDocument(), protectedProps);
        } else if (request.getContent().isArray()) {
            contains = request.getContent().asArray().stream().map(doc -> doc.asDocument()).anyMatch(doc -> contains(doc, protectedProps));
        } else {
            contains = false;
        }

        if (contains) {
            response.setStatusCode(HttpStatus.SC_FORBIDDEN);
            request.setInError(true);
        }
    }

    private boolean contains(BsonDocument doc, Set<String> protectedProps) {
        var ufdoc = JsonUtils.unflatten(doc).asDocument();
        return protectedProps.stream().anyMatch(hiddenProp -> contains(ufdoc, hiddenProp));
    }

    private boolean contains(BsonDocument doc, String protectedProps) {
        // let's check update operators first, since doc can look like:
        // {
        //    <operator1>: { <field1>: <value1>, ... },
        //    <operator2>: { <field2>: <value2>, ... },
        //    ...
        // }

        if (JsonUtils.containsUpdateOperators(doc)) {
            var updateOperators = doc.keySet().stream().filter(k -> k.startsWith("$")).collect(Collectors.toList());

            var propInUpdateOperators = updateOperators.stream().anyMatch(uo -> contains(doc.get(uo).asDocument(), protectedProps));

            if (propInUpdateOperators) {
                return true;
            }
        }

        if (protectedProps.contains(".")) {
            var first = protectedProps.substring(0, protectedProps.indexOf("."));
            if (first.length() > 0 && doc.containsKey(first) && doc.get(first).isDocument()) {
                return contains(doc.get(first).asDocument(), protectedProps.substring(protectedProps.indexOf(".")+1));
            } else {
                return false;
            }
        } else {
            return protectedProps.length() > 0 && doc.containsKey(protectedProps);
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
