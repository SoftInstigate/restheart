/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.handlers.changestreams;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.bson.BsonDocument;
import org.bson.json.JsonMode;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.URLUtils;

import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.spi.WebSocketHttpExchange;

/**
 * Idendifies a ChangeStreamWorker
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ChangeStreamWorkerKey {
    private final String url;
    private final BsonDocument avars;
    private final JsonMode jsonMode;

    public ChangeStreamWorkerKey(String url, BsonDocument avars, JsonMode jsonMode) {
        this.url = url;
        this.avars = avars;
        this.jsonMode = jsonMode;
    }

    public ChangeStreamWorkerKey(WebSocketHttpExchange exchange) {
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

    public ChangeStreamWorkerKey(HttpServerExchange exchange) {
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
        if (!(obj instanceof ChangeStreamWorkerKey)) {
            return false;
        } else {
            return obj.hashCode() == this.hashCode();
        }
    }

    @Override
    public String toString() {
        var _url = this.url == null ? null : URLUtils.decodeQueryString(this.url);

        return "ChangeStreamWorkerKey{url: " + _url + ", avars: " + BsonUtils.toJson(this.avars) + ", jsonMode: " + this.jsonMode + "}";
    }

    private static String encode(String queryString) {
        return URLEncoder.encode(URLUtils.decodeQueryString(queryString), StandardCharsets.UTF_8);
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
