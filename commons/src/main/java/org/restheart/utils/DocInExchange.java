package org.restheart.utils;

import org.bson.BsonDocument;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.AttachmentKey;

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
