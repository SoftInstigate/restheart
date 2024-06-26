/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.stream.StreamSupport;

import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import static org.restheart.exchange.ExchangeKeys.FALSE_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.FILE_METADATA;
import static org.restheart.exchange.ExchangeKeys.MAX_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.MIN_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.NULL_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.PROPERTIES;
import static org.restheart.exchange.ExchangeKeys.TRUE_KEY_ID;
import static org.restheart.exchange.ExchangeKeys._ID;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.ChannelReader;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.LambdaUtils;
import org.restheart.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;

/**
 *
 * Injects the request content to MongoRequest
 *
 * also check the Content-Type header in case the content is not empty
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoRequestContentInjector {
    static final Logger LOGGER = LoggerFactory.getLogger(MongoRequestContentInjector.class);

    private static final String ERROR_INVALID_CONTENTTYPE = "Content-Type must be either: " + Exchange.JSON_MEDIA_TYPE + " or " + Exchange.HAL_JSON_MEDIA_TYPE;

    private static boolean isHalOrJson(final HeaderValues contentTypes) {
        return (contentTypes == null
                || contentTypes.isEmpty())
                || (contentTypes.stream().anyMatch(ct -> ct.startsWith(Exchange.HAL_JSON_MEDIA_TYPE)
                || ct.startsWith(Exchange.JSON_MEDIA_TYPE)));
    }

    private static boolean isFormOrMultipart(final HeaderValues contentTypes) {
        return contentTypes != null
                && !contentTypes.isEmpty()
                && contentTypes.stream().anyMatch(ct -> ct.startsWith(Exchange.FORM_URLENCODED) || ct.startsWith(Exchange.MULTIPART));
    }

    /**
     * Checks the _id in POST requests; it cannot be a string having a special
     * meaning e.g _null, since the URI /db/coll/_null refers to the document
     * with _id: null
     *
     * @param content
     * @return null if ok, or the first not valid id
     */
    public static String checkReservedId(BsonValue content) {
        if (content == null) {
            return null;
        } else if (content.isDocument()) {
            var id = content.asDocument().get("_id");

            if (id == null || !id.isString()) {
                return null;
            }

            var _id = id.asString().getValue();

            if (MAX_KEY_ID.equalsIgnoreCase(_id)
                    || MIN_KEY_ID.equalsIgnoreCase(_id)
                    || NULL_KEY_ID.equalsIgnoreCase(_id)
                    || TRUE_KEY_ID.equalsIgnoreCase(_id)
                    || FALSE_KEY_ID.equalsIgnoreCase(_id)) {
                return _id;
            } else {
                return null;
            }
        } else if (content.isArray()) {
            var arrayContent = content.asArray();

            var objs = arrayContent.getValues().iterator();

            String ret = null;

            while (objs.hasNext()) {
                var obj = objs.next();

                if (obj.isDocument()) {
                    ret = checkReservedId(obj);
                    if (ret != null) {
                        break;
                    }
                } else {
                    LOGGER.warn("element of content array is not an object");
                }
            }

            return ret;
        }

        LOGGER.warn("content is not an object nor an array");
        return null;
    }

    /**
     * Search the request for a field named 'metadata' (or 'properties') which
     * must contain valid JSON
     *
     * @param formData
     * @return the parsed BsonDocument from the form data or an empty
     * BsonDocument
     */
    protected static BsonDocument extractMetadata(final FormData formData) throws BadRequestException {
        var metadataString = formData.getFirst(FILE_METADATA) != null
                ? formData.getFirst(FILE_METADATA).getValue()
                : formData.getFirst(PROPERTIES) != null
                ? formData.getFirst(PROPERTIES).getValue()
                : null;

        if (metadataString != null) {
            var parsed = BsonUtils.parse(metadataString);

            if (parsed == null) {
                return new BsonDocument();
            } else if (parsed.isDocument()) {
                return parsed.asDocument();
            } else {
                throw new BadRequestException("metadata is not a valid JSON object");
            }
        } else {
            return new BsonDocument();
        }
    }

    /**
     * Find the name of the first file field in this request
     *
     * @param formData
     * @return the first file field name or null
     */
    private static String extractFileField(final FormData formData) {
        String fileField = null;
        for (var f : formData) {
            if (formData.getFirst(f) != null && formData.getFirst(f).isFileItem()) {
                fileField = f;
                break;
            }
        }
        return fileField;
    }

    private static final FormParserFactory FORM_PARSER = FormParserFactory.builder().withDefaultCharset(StandardCharsets.UTF_8.name()).build();

    /**
     *
     * @param exchange
     * @return the parsed bson
     * @throws org.restheart.exchange.BadRequestException
     * @throws java.io.IOException
     */
    public static BsonValue inject(final HttpServerExchange exchange) throws BadRequestException, IOException {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isGet() || request.isOptions() || request.isDelete()) {
            return null;
        }

        BsonValue content;

        final var contentType = request.getHeaders().get(Headers.CONTENT_TYPE);

        if (contentType == null) {
            content = injectBson(exchange); // if no content type is specified assume is application/json
        } else if (isFormOrMultipart(contentType)) {
            content = injectMultipart(exchange, request, response);
        } else if (isHalOrJson(contentType)) {
            content = injectBson(exchange);
        } else {
            throw new BadRequestException(ERROR_INVALID_CONTENTTYPE, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }

        if (content == null) {
            content = new BsonDocument();
        } else if (content.isArray()) {
            if (!(request.isCollection() && request.isPost()) &&
                !(request.isDocument() && request.isPatch())) {
                throw new BadRequestException("request content must be a Json object");
            }

            if (!content.asArray().stream().anyMatch(_doc -> {
                if (_doc.isDocument()) {
                    var _id = _doc.asDocument().get(_ID);

                    if (_id != null && _id.isArray()) {
                        throw new BadRequestException("the type of _id in request data is not supported: " + (_id == null ? "" : _id.getBsonType().name()));
                    }
                    return true;
                } else {
                    throw new BadRequestException("request data must be either an json object or an array of objects");
                }
            })) {
                // an error occurred
                return null;
            }
        } else if (content.isDocument()) {
            var _content = content.asDocument();

            var _id = _content.get(_ID);

            if (_id != null && _id.isArray()) {
                throw new BadRequestException("the type of _id in request data is not supported: " + _id.getBsonType().name());
            }
        }

        // For POST and PUT we use insertOne (wm=insert) or findOneAndReplace (wm=update|upserts)
        // that require a document (cannot use update operators)
        // note that from MongoDB 5 a field name can be equal to an update operator
        // we forbid this to avoid unexpected behavior
        if (request.isPost() || request.isPut()) {
            if (BsonUtils.containsUpdateOperators(content, true)) {
                // not acceptable
                throw new BadRequestException("update operators (but $currentDate) cannot be used on POST and PUT requests");
            }

            // unflatten request content for POST and PUT requests
            content = BsonUtils.unflatten(content);
        }

        return content;
    }

    private static BsonValue injectBson(HttpServerExchange exchange) throws BadRequestException, IOException {
        BsonValue content;
        final String contentString;

        var bar = ByteArrayProxyRequest.of(exchange);

        if (bar.isContentAvailable()) {
            // if content has been already injected
            // get it from MongoRequest.readContent()
            contentString = new String(bar.readContent(), StandardCharsets.UTF_8);
        } else {
            // otherwise use ChannelReader
            contentString = ChannelReader.readString(exchange);
        }

        // parse the json content
        if (contentString != null && !contentString.isEmpty()) { // check content type
            try {
                content = BsonUtils.parse(contentString);

                if (content != null && !content.isDocument() && !content.isArray()) {
                    throw new BadRequestException("request data must be either a json object or an array, got " + content.getBsonType().name());
                }
            } catch (JsonParseException | IllegalArgumentException ex) {
                throw new BadRequestException("Invalid JSON. " + ex.getMessage(), ex);
            }
        } else {
            content = null;
        }

        return content;
    }

    private static FormDataParser parser(HttpServerExchange exchange) {
        // form data requires exchange.startBlocking(); called by WorkingThreadsPoolDispatcher

        return FORM_PARSER.createParser(exchange);
    }

    private static BsonValue injectMultipart(HttpServerExchange exchange, MongoRequest request, MongoResponse response) throws BadRequestException, IOException {
        // form data requires exchange.startBlocking(); called by WorkingThreadsPoolDispatcher

        if (request.isWriteDocument() && (request.isFile() || request.isFilesBucket())) {
             return injectMultiparForFiles(exchange, request, response);
        }

        var parser = parser(exchange);

        if (parser == null) {
            throw new BadRequestException("There is no form parser registered for the request content type", HttpStatus.SC_BAD_REQUEST);
        }

        var formData = parser.parseBlocking();

        var ret = new BsonDocument();
        boolean errored[] = {false};

        StreamSupport.stream(formData.spliterator(), false)
            .map(partName -> new Pair<String, Deque<FormData.FormValue>>(partName, formData.get(partName)))
            .filter(part -> !part.getValue().isEmpty())
            .map(part -> new Pair<String, FormData.FormValue>(part.getKey(), part.getValue().getFirst()))
            .filter(part -> !part.getValue().isFileItem())
            .forEach(part -> {
                try {
                    var value = part.getValue().getValue();

                    if (value == null) {
                        ret.put(part.getKey(), BsonNull.VALUE);
                    } else if (value.isBlank()) {
                        ret.put(part.getKey(), new BsonString(value));
                    } else {
                        ret.put(part.getKey(), BsonUtils.parse(part.getValue().getValue()));
                    }
                } catch(JsonParseException jpe) {
                    var strippedValue = part.getValue().getValue().strip();
                    if (strippedValue.startsWith("{") || strippedValue.startsWith("[")) {
                        LambdaUtils.throwsSneakyException(new BadRequestException("Invalid JSON. " + jpe.getMessage(), jpe));
                        errored[0] = true;
                    } else {
                        ret.put(part.getKey(), new BsonString(part.getValue().getValue()));
                    }
                }
            });

        return errored[0] ? null : ret;
    }

    private static BsonValue injectMultiparForFiles(HttpServerExchange exchange, MongoRequest request, MongoResponse response)  throws BadRequestException, IOException {
        BsonValue content;

        var parser = parser(exchange);

        if (parser == null) {
            throw new BadRequestException("There is no form parser registered for the request content type", HttpStatus.SC_BAD_REQUEST);
        }

        var formData = parser.parseBlocking();

        try {
            content = extractMetadata(formData);
        } catch (JsonParseException | IllegalArgumentException ex) {
            throw new BadRequestException("Invalid data: 'metadata' field is not a valid JSON object", ex);
        }

        final var fileField = extractFileField(formData);

        if (fileField == null) {
            throw new BadRequestException("This request does not contain any binary file");
        }

        final InputStream fileInputStream;

        try {
            fileInputStream = formData.getFirst(fileField).getFileItem().getInputStream();
            request.setFileInputStream(fileInputStream);
        } catch(IOException ioe) {
            response.addWarning("error getting binary field from request");
            LOGGER.warn("error getting binary field from request", ioe);
            throw ioe;
        }

        return content;
    }
}
