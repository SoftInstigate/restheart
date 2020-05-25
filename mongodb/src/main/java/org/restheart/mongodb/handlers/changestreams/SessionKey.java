/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package org.restheart.mongodb.handlers.changestreams;

import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.bson.BsonDocument;
import org.bson.json.JsonMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SessionKey {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(SessionKey.class);
    
    private final String url;
    private final BsonDocument avars;
    private final JsonMode jsonMode;

    public SessionKey(String url, BsonDocument avars, JsonMode jsonMode) {
        this.url = url;
        this.avars = avars;
        this.jsonMode = jsonMode;
    }

    public SessionKey(WebSocketHttpExchange exchange) {
        if (!exchange.getQueryString().isEmpty()) {
            var qstring = encode("?".concat(exchange.getQueryString()));
            var uri = encode(exchange.getRequestURI());
            uri = uri.replace(qstring, "");
            
            this.url = uri;
        } else {
            this.url = encode(exchange.getRequestURI());
        }
        
        this.avars = exchange.getAttachment(GetChangeStreamHandler.AVARS_ATTACHMENT_KEY);
        this.jsonMode = exchange.getAttachment(GetChangeStreamHandler.JSON_MODE_ATTACHMENT_KEY);
    }
    
    public SessionKey(HttpServerExchange exchange) {
        this.url = encode(exchange.getRequestPath());
        
        this.avars = exchange.getAttachment(GetChangeStreamHandler.AVARS_ATTACHMENT_KEY);
        this.jsonMode = exchange.getAttachment(GetChangeStreamHandler.JSON_MODE_ATTACHMENT_KEY);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUrl(), getAvars(), getJsonMode());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SessionKey)) {
            return false;
        } else {
            return obj.hashCode() == this.hashCode();
        }
    }

    @Override
    public String toString() {
        return "" + hashCode();
    }
    
    
    
    private static String encode(String queryString) {
        return URLEncoder.encode(
                URLDecoder.decode(queryString,
                        StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
    }

    /**
     * @return the url
     */
    public String getUrl() {
        return url;
    }

    /**
     * @return the avars
     */
    public BsonDocument getAvars() {
        return avars;
    }

    /**
     * @return the jsonMode
     */
    public JsonMode getJsonMode() {
        return jsonMode;
    }
}
