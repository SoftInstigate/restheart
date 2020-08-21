/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.exchange;

import com.google.common.reflect.TypeToken;
import com.mongodb.MongoClient;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.UpdateResult;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.*;
import org.bson.conversions.Bson;
import org.bson.json.JsonParseException;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.JsonUtils;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 *
 * Response implementation used by MongoService and backed by BsonValue that
 * provides simplify methods to deal mongo response
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class MongoResponse extends BsonResponse {
    private final static ReplaceOptions R_NOT_UPSERT_OPS = new ReplaceOptions()
            .upsert(false);
    
    static {
        LOGGER = LoggerFactory.getLogger(MongoResponse.class);
    }

    private OperationResult dbOperationResult;

    private final List<String> warnings = new ArrayList<>();

    private long count = -1;

    protected MongoResponse(HttpServerExchange exchange) {
        super(exchange);
    }

    public static MongoResponse init(HttpServerExchange exchange) {
        return new MongoResponse(exchange);
    }

    public static MongoResponse of(HttpServerExchange exchange) {
        return of(exchange, MongoResponse.class);
    }

    private static final Type _TYPE = new TypeToken<MongoResponse>(MongoResponse.class) {
    }.getType();

    public static Type type() {
        return _TYPE;
    }

    @Override
    public String readContent() {
        var request = Request.of(wrapped);
        BsonValue tosend;

        if (!request.isGet() && (content == null || content.isDocument())) {
            tosend = addWarnings(content == null ? null : content.asDocument());
        } else {
            tosend = content;
        }

        if (tosend != null) {
            if (request instanceof MongoRequest) {
                return JsonUtils.toJson(tosend, ((MongoRequest) request).getJsonMode());
            } else {
                return JsonUtils.toJson(tosend);
            }
        } else {
            return null;
        }
    }

    private BsonDocument addWarnings(BsonDocument content) {
        if (content != null) {
            if (warnings != null
                    && !warnings.isEmpty()
                    && content.isDocument()) {
                var contentWithWarnings = new BsonDocument();

                var ws = new BsonArray();

                warnings.stream()
                        .map(w -> new BsonString(w))
                        .forEachOrdered(ws::add);

                contentWithWarnings.put("_warnings", ws);

                contentWithWarnings.putAll(content.asDocument());

                return contentWithWarnings;
            } else {
                return content;
            }
        } else if (warnings != null
                && !warnings.isEmpty()) {
            var contentWithWarnings = new BsonDocument();

            var ws = new BsonArray();

            warnings.stream()
                    .map(w -> new BsonString(w))
                    .forEachOrdered(ws::add);

            contentWithWarnings.put("_warnings", ws);

            return contentWithWarnings;
        } else {
            return content;
        }
    }

    /**
     * @return the dbOperationResult
     */
    public OperationResult getDbOperationResult() {
        return dbOperationResult;
    }

    /**
     * @param dbOperationResult the dbOperationResult to set
     */
    public void setDbOperationResult(OperationResult dbOperationResult) {
        this.dbOperationResult = dbOperationResult;
    }

    /**
     * @return the warnings
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * @param warning
     */
    public void addWarning(String warning) {
        warnings.add(warning);
    }

    /**
     *
     * @param code
     * @param message
     * @param t
     */
    @Override
    public void setInError(
            int code,
            String message,
            Throwable t) {
        setStatusCode(code);

        String httpStatusText = HttpStatus.getStatusText(code);

        setInError(true);

        setContent(getErrorContent(code,
                httpStatusText,
                message,
                t, false));
    }

    /**
     * @return the count
     */
    public long getCount() {
        return count;
    }

    /**
     * @param count the count to set
     */
    public void setCount(long count) {
        this.count = count;
    }

    /**
     *
     * @param href
     * @param code
     * @param response
     * @param httpStatusText
     * @param message
     * @param t
     * @param includeStackTrace
     * @return
     */
    private BsonDocument getErrorContent(int code,
            String httpStatusText,
            String message,
            Throwable t,
            boolean includeStackTrace) {
        var rep = new BsonDocument();

        rep.put("http status code",
                new BsonInt32(code));
        rep.put("http status description",
                new BsonString(httpStatusText));
        if (message != null) {
            rep.put("message", new BsonString(
                    avoidEscapedChars(message)));
        }

        if (t != null) {
            rep.put(
                    "exception",
                    new BsonString(t.getClass().getName()));

            if (t.getMessage() != null) {
                if (t instanceof JsonParseException) {
                    rep.put("exception message",
                            new BsonString("invalid json"));
                } else {
                    rep.put("exception message",
                            new BsonString(avoidEscapedChars(t.getMessage())));
                }
            }

            if (includeStackTrace) {
                BsonArray stackTrace = getStackTrace(t);

                if (stackTrace != null) {
                    rep.put("stack trace", stackTrace);
                }
            }
        }

        var _warnings = new BsonArray();

        // add warnings
        if (getWarnings() != null && !getWarnings().isEmpty()) {
            getWarnings().forEach(w -> _warnings.add(new BsonString(w)));

            rep.put("_warnings", _warnings);
        }

        return rep;
    }

    private BsonArray getStackTrace(Throwable t) {
        if (t == null || t.getStackTrace() == null) {
            return null;
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        String st = sw.toString();
        st = avoidEscapedChars(st);
        String[] lines = st.split("\n");

        BsonArray list = new BsonArray();

        for (String line : lines) {
            list.add(new BsonString(line));
        }

        return list;
    }

    private String avoidEscapedChars(String s) {
        return s == null
                ? null
                : s
                        .replaceAll("\"", "'")
                        .replaceAll("\t", "  ");
    }
    
    /**
     * Helper method to restore a modified document. rollback() can be used when
     * verifing a document after being updated to rollback changes. A common use
     * case is when the request body contains update operators and an
     * Interceptor cannot verify it at InterceptPoint.REQUEST time; it can check
     * it at InterceptPoint.RESPONSE time and restore data if the updated
     * document doest not fullfil the required conditions.
     *
     * Note: rollback() does not support bulk updates.
     *
     * @param mclient
     * @param request
     * @param response
     * @throws Exception
     */
    public void rollback(MongoClient mclient)
            throws Exception {
        var request = MongoRequest.of(getExchange());
        
        if (request.isBulkDocuments()
                || (request.isPost() && request.getContent() != null
                && request.getContent().isArray())) {
            throw new UnsupportedOperationException("rollback() does not support "
                    + "bulk updates");
        }

        var exchange = request.getExchange();

        MongoDatabase mdb = mclient.getDatabase(request.getDBName());

        MongoCollection<BsonDocument> coll = mdb.getCollection(
                request.getCollectionName(),
                BsonDocument.class);

        BsonDocument oldData = getDbOperationResult().getOldData();

        Object newEtag = getDbOperationResult().getEtag();

        if (oldData != null) {
            // document was updated, restore old one
            restoreDocument(
                    request.getClientSession(),
                    coll,
                    oldData.get("_id"),
                    request.getShardKey(),
                    oldData,
                    newEtag,
                    "_etag");

            // add to response old etag
            if (oldData.get("$set") != null
                    && oldData.get("$set").isDocument()
                    && oldData.get("$set")
                            .asDocument()
                            .get("_etag") != null) {
                exchange.getResponseHeaders().put(Headers.ETAG,
                        oldData.get("$set")
                                .asDocument()
                                .get("_etag")
                                .asObjectId()
                                .getValue()
                                .toString());
            } else {
                exchange.getResponseHeaders().remove(Headers.ETAG);
            }

        } else {
            // document was created, delete it
            Object newId = getDbOperationResult().getNewData().get("_id");

            coll.deleteOne(and(eq("_id", newId), eq("_etag", newEtag)));
        }
    }

    private static boolean restoreDocument(
            final ClientSession cs,
            final MongoCollection<BsonDocument> coll,
            final Object documentId,
            final BsonDocument shardKeys,
            final BsonDocument data,
            final Object etag,
            final String etagLocation) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(data);

        Bson query;

        if (etag == null) {
            query = eq("_id", documentId);
        } else {
            query = and(eq("_id", documentId), eq(
                    etagLocation != null && !etagLocation.isEmpty()
                    ? etagLocation
                    : "_etag", etag));
        }

        if (shardKeys != null) {
            query = and(query, shardKeys);
        }

        UpdateResult result = cs == null
                ? coll.replaceOne(query, data, R_NOT_UPSERT_OPS)
                : coll.replaceOne(cs, query, data, R_NOT_UPSERT_OPS);

        return result.getModifiedCount() == 1;
    }
}
