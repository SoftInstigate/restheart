package org.restheart.metadata.transformers;

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.BsonString;
import org.bson.BsonArray;
import org.bson.BsonNull;
import org.restheart.handlers.RequestContext;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Set;
import org.restheart.handlers.metadata.InvalidMetadataException;

public class DocumentAccessTransformer implements Transformer {

    /**
     * rts:='[{"name":"userFilter","phase":"REQUEST","scope":"CHILDREN","args":{"roles":["users"],"key":"email"}}]'
     * Force the authenticated userName as a filter on key.
     * Force the authenticated userName as a key when inserting or updating document.
     * Role based.
     *
     * @param exchange
     * @param context
     * @param contentToTransform
     * @param args
     */
    @Override
    public void transform(
            final HttpServerExchange exchange,
            final RequestContext context,
            BsonValue contentToTransform,
            final BsonValue args) {

        if (args == null || !args.isDocument()) {
            context.addWarning(
                (args == null ? "missing '" : "invalid '") +
                "Transformer arg must be a document: {'key': 'email','roles': [ 'admin' ]}.");
            return;
        }

        BsonValue _roles = args.asDocument().get("roles");
        if (_roles == null || !_roles.isArray()) {
            context.addWarning(
                (_roles == null ? "missing '" : "invalid '") +
                "Transformer roles arg must be array: {'key': 'email','roles': [ 'admin' ]}.");
            return;
        }
        BsonArray roles = _roles.asArray();

        BsonValue _key = args.asDocument().get("key");
        if (_key == null || !_key.isString()) {
            context.addWarning(
                (_key == null ? "missing '" : "invalid '") +
                "Transformer key arg must be string: {'key': 'email','roles': [ 'admin' ]}.");
            return;
        }
        String key = _key.asString().getValue();

        Set<String> userRoles = context.getAuthenticatedAccount().getRoles();

        BsonString userName = new BsonString(context.getAuthenticatedAccount().getPrincipal().getName());

        roles.forEach(_role -> {
            String role = _role.asString().getValue();
            if (userRoles.contains(role)) {
                Deque<String> filters = context.getFilter();

                if (filters == null) {
                    filters = new ArrayDeque<String>();
                    context.setFilter(filters);
                }

                // Force the userName to insert as key
                BsonDocument _contentToTransform = contentToTransform.asDocument();
                _contentToTransform.put(key, userName.asString());

                // Add the filter so a user only looks at their own documents.
                BsonDocument filter = new BsonDocument(key, userName);
                filters.add(filter.toJson());

                return;
            }
        });
    }
}
