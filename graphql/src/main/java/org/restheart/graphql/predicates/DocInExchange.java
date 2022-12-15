package org.restheart.graphql.predicates;

import org.bson.BsonDocument;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.AttachmentKey;

/**
 * Helper class to create a void HttpServerExchange with a BsonDocument attachment
 * and retrieve the document from it
 *
 * Needed to make the undertow's predicates work with BsonDocument
 */
public class DocInExchange {
    public static HttpServerExchange exchange(BsonDocument doc) {
        var e = new HttpServerExchange((ServerConnection)null);
        e.putAttachment(DOC_KEY, doc);
        return e;
    }

    public static BsonDocument doc(HttpServerExchange exchange) {
        return exchange.getAttachment(DOC_KEY);
    }

    private static AttachmentKey<BsonDocument> DOC_KEY = AttachmentKey.create(BsonDocument.class);
}
