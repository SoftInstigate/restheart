/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.json.JsonMode;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.QueryNotFoundException;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.mongodb.utils.StagesInterpolator;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.URLUtils;

import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.spi.WebSocketHttpExchange;

/**
 * Identifies a ChangeStreamWorker: the URL of the stream, its resolved stages and the JSON mode.
 *
 * <p>Nothing else of the request is part of it (since 9.16): two clients of a stream share the
 * worker, and its cursor, when its stages resolve the same, whoever they are and whatever their
 * {@code notify_when} variable, which is evaluated per session on the shared events.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ChangeStreamWorkerKey {
    private final String url;
    private final BsonArray stages;
    private final JsonMode jsonMode;

    public ChangeStreamWorkerKey(String url, BsonArray stages, JsonMode jsonMode) {
        this.url = url;
        this.stages = stages;
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

        this.stages = exchange.getAttachment(GetChangeStreamHandler.STAGES_ATTACHMENT_KEY);
        this.jsonMode = exchange.getAttachment(GetChangeStreamHandler.JSON_MODE_ATTACHMENT_KEY);
    }

    public ChangeStreamWorkerKey(HttpServerExchange exchange) {
        this.url = encode(exchange.getRequestPath());

        this.stages = exchange.getAttachment(GetChangeStreamHandler.STAGES_ATTACHMENT_KEY);
        this.jsonMode = exchange.getAttachment(GetChangeStreamHandler.JSON_MODE_ATTACHMENT_KEY);
    }

    /**
     * The key of the worker that serves {@code request}: the one the request opens or shares. It is
     * built as the change stream handler builds it, the stages of the stream resolved with the
     * variables of the request, so that a plugin can tell whether a request opens a new cursor
     * (for instance to cap the cursors of a database) without repeating that logic.
     *
     * @param request a change stream request, with the collection properties loaded
     * @return the key of the worker for the request
     * @throws QueryNotFoundException if the collection does not declare the stream
     * @throws QueryVariableNotBoundException if a variable of the stages is not bound
     * @throws InvalidMetadataException if the definition of the stream is not valid
     * @since 9.16.0
     */
    public static ChangeStreamWorkerKey of(MongoRequest request)
            throws InvalidMetadataException, QueryVariableNotBoundException, QueryNotFoundException {
        var avars = request.getAggregationVars() == null ? new BsonDocument() : request.getAggregationVars().clone();
        StagesInterpolator.injectAvars(request, avars);

        var stages = new BsonArray();
        GetChangeStreamHandler.resolveStages(request, avars).forEach(stages::add);

        return new ChangeStreamWorkerKey(encode(request.getExchange().getRequestPath()), stages, request.getJsonMode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUrl(), getStages(), getJsonMode());
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

        return "ChangeStreamWorkerKey{url: " + _url + ", stages: " + BsonUtils.toJson(this.stages) + ", jsonMode: " + this.jsonMode + "}";
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
     * @return the resolved stages of the stream
     */
    public BsonArray getStages() {
        return stages;
    }

    /**
     * @return the jsonMode
     */
    public JsonMode getJsonMode() {
        return jsonMode;
    }
}
