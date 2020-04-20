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
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import static org.restheart.exchange.AbstractExchange.LOGGER;
import org.restheart.representation.Resource;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.JsonUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * Response implementation used by MongoService and backed by BsonValue that
 * provides simplify methods to deal mongo response
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class MongoResponse extends BsonResponse {
    private OperationResult dbOperationResult;

    private final List<String> warnings = new ArrayList<>();

    protected MongoResponse(HttpServerExchange exchange) {
        super(exchange);
        LOGGER = LoggerFactory.getLogger(MongoResponse.class);
    }

    public static MongoResponse init(HttpServerExchange exchange) {
        return new MongoResponse(exchange);
    }

    public static MongoResponse of(HttpServerExchange exchange) {
        return of(exchange, MongoResponse.class);
    }

    public static Type type() {
        var typeToken = new TypeToken<MongoResponse>(MongoResponse.class) {
        };

        return typeToken.getType();
    }

    @Override
    public String readContent() {
        if (content != null) {
            return JsonUtils.toJson(content,
                    MongoRequest.of(wrapped).getJsonMode());
        } else {
            return null;
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

        setContent(getErrorContent(
                wrapped.getRequestPath(),
                code,
                httpStatusText,
                message,
                t, false)
                .asBsonDocument());

        transformError();

        // This makes the content availabe to BufferedByteArrayResponse
        // core's ResponseSender uses BufferedResponse 
        // to send the content to the client
        if (getContent() != null) {
            var bar = BufferedByteArrayResponse.of(wrapped);

            bar.setContentTypeAsJson();

            try {
                bar.writeContent(
                        JsonUtils.toJson(getContent(),
                                MongoRequest.of(wrapped).getJsonMode())
                                .getBytes());
            } catch (IOException ioe) {
                //LOGGER.error("Error writing request content", ioe);
            }
        }
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
    private Resource getErrorContent(String href,
            int code,
            String httpStatusText,
            String message,
            Throwable t,
            boolean includeStackTrace) {
        var rep = new Resource(href);

        rep.addProperty("http status code",
                new BsonInt32(code));
        rep.addProperty("http status description",
                new BsonString(httpStatusText));
        if (message != null) {
            rep.addProperty("message", new BsonString(
                    avoidEscapedChars(message)));
        }

        Resource nrep = new Resource();

        if (t != null) {
            nrep.addProperty(
                    "exception",
                    new BsonString(t.getClass().getName()));

            if (t.getMessage() != null) {
                if (t instanceof JsonParseException) {
                    nrep.addProperty("exception message",
                            new BsonString("invalid json"));
                } else {
                    nrep.addProperty("exception message",
                            new BsonString(avoidEscapedChars(t.getMessage())));
                }
            }

            if (includeStackTrace) {
                BsonArray stackTrace = getStackTrace(t);

                if (stackTrace != null) {
                    nrep.addProperty("stack trace", stackTrace);
                }
            }

            rep.addChild("rh:exception", nrep);
        }

        // add warnings
        if (getWarnings() != null) {
            getWarnings().forEach(w -> rep.addWarning(w));
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
     * Tranforms the error document to the target representation format.
     */
    private void transformError() {
        var contentToTransform = getContent();
        var errorContent = new BsonDocument();

        var rf = MongoRequest.of(wrapped).getRepresentationFormat();

        final boolean isStandardRepresentation
                = rf == ExchangeKeys.REPRESENTATION_FORMAT.STANDARD
                || rf == ExchangeKeys.REPRESENTATION_FORMAT.S;

        if (contentToTransform == null
                || (!isStandardRepresentation
                && rf != ExchangeKeys.REPRESENTATION_FORMAT.SHAL
                && rf != ExchangeKeys.REPRESENTATION_FORMAT.PLAIN_JSON
                && rf != ExchangeKeys.REPRESENTATION_FORMAT.PJ)) {
            return;
        }

        setContentType(Resource.JSON_MEDIA_TYPE);

        if (contentToTransform.isDocument()) {
            BsonValue _embedded = contentToTransform
                    .asDocument()
                    .get("_embedded");

            if (_embedded != null) {
                BsonDocument embedded = _embedded.asDocument();

                // add _warnings if any
                BsonArray _warnings = new BsonArray();
                addItems(_warnings, embedded, "rh:warnings");

                if (!_warnings.isEmpty()) {
                    errorContent.append("_warnings", _warnings);
                }

                // add _errors if any
                BsonArray _errors = new BsonArray();
                addItems(_errors, embedded, "rh:error");

                if (!_errors.isEmpty()) {
                    errorContent.append("_errors", _errors);
                }

                // add _results if any
                if (embedded.containsKey("rh:result")) {
                    BsonArray bulkResp = embedded.get("rh:result")
                            .asArray();

                    if (bulkResp.size() > 0) {
                        BsonValue el = bulkResp.get(0);

                        if (el.isDocument()) {
                            BsonDocument doc = el.asDocument();

                            doc
                                    .keySet()
                                    .stream()
                                    .forEach(key
                                            -> errorContent
                                            .append(key, doc.get(key)));
                        }
                    }
                }

                // add _exception if any
                BsonArray _exception = new BsonArray();
                addItems(_exception, embedded, "rh:exception");

                if (!_exception.isEmpty()) {
                    errorContent.append("_exceptions", _exception);
                }
            }
        }

        if (isInError()) {
            contentToTransform.asDocument().keySet().stream()
                    .filter(
                            key -> !"_embedded".equals(key)
                            && !"_links".equals(key))
                    .forEach(key -> errorContent.append(key,
                    contentToTransform
                            .asDocument()
                            .get(key)));

        }

        setContent(errorContent);
    }

    private void addItems(BsonArray elements, BsonDocument items, String ns) {
        if (items.containsKey(ns)) {
            elements.addAll(
                    items
                            .get(ns)
                            .asArray());
        }
    }
}
