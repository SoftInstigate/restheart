package org.restheart.graphql.predicates;

import org.bson.BsonValue;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.AttachmentKey;

/**
 * Helper class to create a void HttpServerExchange with a BsonValue attachment
 * and retrieve the document from it
 *
 * Needed to make the undertow's predicates work with BsonValue
 */
public class ExchangeWithBsonValue {
    public static HttpServerExchange exchange(BsonValue value) {
        var e = new HttpServerExchange((ServerConnection)null);
        e.putAttachment(DOC_KEY, value);
        return e;
    }

    public static BsonValue value(HttpServerExchange exchange) {
        return exchange.getAttachment(DOC_KEY);
    }

    private static AttachmentKey<BsonValue> DOC_KEY = AttachmentKey.create(BsonValue.class);
}
