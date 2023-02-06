/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
package org.restheart.mongodb.exchange;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.tika.Tika;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import org.restheart.exchange.ByteArrayProxyRequest;
import org.restheart.exchange.Exchange;
import static org.restheart.exchange.ExchangeKeys.FALSE_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.FILE_METADATA;
import static org.restheart.exchange.ExchangeKeys.MAX_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.MIN_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.NULL_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.PROPERTIES;
import static org.restheart.exchange.ExchangeKeys.TRUE_KEY_ID;
import static org.restheart.exchange.ExchangeKeys._ID;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.utils.ChannelReader;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Injects the request content to MongoRequest from BufferedByteArrayRequest
 * buffer
 *
 * also check the Content-Type header in case the content is not empty
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoRequestContentInjector {

    static final Logger LOGGER = LoggerFactory.getLogger(MongoRequestContentInjector.class);

    private static final String CONTENT_TYPE = "contentType";

    private static final String ERROR_INVALID_CONTENTTYPE = "Content-Type must be either: " + Exchange.JSON_MEDIA_TYPE + " or " + Exchange.HAL_JSON_MEDIA_TYPE;

    private static final String ERROR_INVALID_CONTENTTYPE_FILE = "Content-Type must be either: " + Exchange.APP_FORM_URLENCODED_TYPE + " or " + Exchange.MULTIPART_FORM_DATA_TYPE;

    private static boolean isHalOrJson(final HeaderValues contentTypes) {
        return (contentTypes == null
                || contentTypes.isEmpty())
                || (contentTypes.stream().anyMatch(ct -> ct.startsWith(Exchange.HAL_JSON_MEDIA_TYPE)
                || ct.startsWith(Exchange.JSON_MEDIA_TYPE)));
    }

    private static boolean isFormOrMultipart(final HeaderValues contentTypes) {
        return contentTypes != null
                && !contentTypes.isEmpty()
                && contentTypes.stream().anyMatch(ct -> ct.startsWith(Exchange.APP_FORM_URLENCODED_TYPE)
                || ct.startsWith(Exchange.MULTIPART_FORM_DATA_TYPE));
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

    private static void injectContentTypeFromFile( final BsonDocument content, final InputStream file) throws IOException {
        if (content.get(CONTENT_TYPE) == null && file != null) {
            final var contentType = detectMediaType(file);
            if (contentType != null) {
                content.append(CONTENT_TYPE, new BsonString(contentType));
            }
        }
    }

    /**
     * Search the request for a field named 'metadata' (or 'properties') which
     * must contain valid JSON
     *
     * @param formData
     * @return the parsed BsonDocument from the form data or an empty
     * BsonDocument
     */
    protected static BsonDocument extractMetadata(final FormData formData) throws JsonParseException {
        var metadata = new BsonDocument();

        final String metadataString;

        metadataString = formData.getFirst(FILE_METADATA) != null
                ? formData.getFirst(FILE_METADATA).getValue()
                : formData.getFirst(PROPERTIES) != null
                ? formData.getFirst(PROPERTIES).getValue()
                : null;

        if (metadataString != null) {
            metadata = BsonDocument.parse(metadataString);
        }

        return metadata;
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

    /**
     * Detect the file's mediatype
     *
     * @param file input file
     * @return the content-type as a String
     * @throws IOException
     */
    public static String detectMediaType(InputStream file) throws IOException {
        return new Tika().detect(file);
    }

    private static final FormParserFactory FORM_PARSER = FormParserFactory.builder().build();

    /**
     * Creates a new instance of BodyInjectorHandler
     *
     */
    public MongoRequestContentInjector() {
    }

    /**
     *
     * @param exchange
     */
    public static void inject(final HttpServerExchange exchange) {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            return;
        }

        if (request.isGet() || request.isOptions() || request.isDelete() || request.getExchange().getRequestContentLength() <= 0) {
            return;
        }

        BsonValue content;

        final var contentType = request.getHeaders().get(Headers.CONTENT_TYPE);

        if (contentType == null) {
            content = null;
        } else if (isFormOrMultipart(contentType)) {
            content = injectMultipart(exchange, request, response);
        } else if (isHalOrJson(contentType)) {
            content = injectBson(exchange, request, response);
        } else {
            response.setInError(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, ERROR_INVALID_CONTENTTYPE);
            return;
        }

        if (content == null) {
            content = new BsonDocument();
        } else if (content.isArray()) {
            if (!request.isCollection() || !request.isPost()) {
                response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "request data can be an array only for POST to collection resources (bulk post)");
                return;
            }

            if (!content.asArray().stream().anyMatch(_doc -> {
                if (_doc.isDocument()) {
                    var _id = _doc.asDocument().get(_ID);

                    if (_id != null && _id.isArray()) {
                        response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "the type of _id in request data is not supported: " + (_id == null ? "" : _id.getBsonType().name()));
                        return false;
                    }
                    return true;
                } else {
                    response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "request data must be either an json object or an array of objects");
                    return false;
                }
            })) {
                // an error occurred
                return;
            }
        } else if (content.isDocument()) {
            var _content = content.asDocument();

            var _id = _content.get(_ID);

            if (_id != null && _id.isArray()) {
                response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "the type of _id in request data is not supported: " + _id.getBsonType().name());
                return;
            }
        }

        // For POST and PUT we use insertOne (wm=insert) or findOneAndReplace (wm=update|upserts)
        // that require a document (cannot use update operators)
        // note that from MongoDB 5 a field name can be equal to an update operator
        // we forbid this to avoid unexpected behavior
        if (request.isPost() || request.isPut()) {
            if (BsonUtils.containsUpdateOperators(content, true)) {
                // not acceptable
                response.setInError(HttpStatus.SC_BAD_REQUEST, "update operators (but $currentDate) cannot be used on POST and PUT requests");
                return;
            }

            // unflatten request content for POST and PUT requests
            content = BsonUtils.unflatten(content);
        }

        request.setContent(content);
    }

    private static BsonValue injectBson(HttpServerExchange exchange, MongoRequest request, MongoResponse response) {
        BsonValue content;
        final String contentString;

        var bar = ByteArrayProxyRequest.of(exchange);

        try {
            if (bar.isContentAvailable()) {
                // if content has been already injected
                // get it from MongoRequest.readContent()
                contentString = new String(bar.readContent(), StandardCharsets.UTF_8);
            } else {
                // otherwise use ChannelReader
                contentString = ChannelReader.readString(exchange);
            }
        } catch (IOException ieo) {
            var errMsg = "Error reading request content";
            LOGGER.error(errMsg, ieo);
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, errMsg);
            return null;
        }

        // parse the json content
        if (contentString != null && !contentString.isEmpty()) { // check content type
            try {
                content = BsonUtils.parse(contentString);

                if (content != null && !content.isDocument() && !content.isArray()) {
                    throw new IllegalArgumentException("request data must be either a json object or an array, got " + content.getBsonType().name());
                }
            } catch (JsonParseException | IllegalArgumentException ex) {
                response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "Invalid JSON. " + ex.getMessage(), ex);
                return null;
            }
        } else {
            content = null;
        }

        return content;
    }

    private static BsonValue injectMultipart(HttpServerExchange exchange, MongoRequest request, MongoResponse response) {
        BsonValue content = null;

        if (!((request.isPost() && request.isFilesBucket()) || (request.isPut() && request.isFile()))) {
            response.setInError(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, ERROR_INVALID_CONTENTTYPE_FILE);
            return null;
        }

        var parser = FORM_PARSER.createParser(exchange);

        if (parser == null) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "There is no form parser registered for the request content type");
            return null;
        }

        FormData formData;

        try {
            formData = parser.parseBlocking();
        } catch (IOException ioe) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "Error parsing the multipart form: data could not be read", ioe);
            return null;
        }

        try {
            content = extractMetadata(formData);
        } catch (JsonParseException | IllegalArgumentException ex) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "Invalid data: 'properties' field is not a valid JSON", ex);
            return null;
        }

        final var fileField = extractFileField(formData);

        if (fileField == null) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "This request does not contain any binary file");
            return null;
        }

        final InputStream fileInputStream;

        try {
            fileInputStream = formData.getFirst(fileField).getFileItem().getInputStream();
            request.setFileInputStream(fileInputStream);
        } catch(IOException ioe) {
            response.addWarning("error getting binary field from request");
            LOGGER.warn("error getting binary field from request", ioe);
            return null;
        }

        try {
            injectContentTypeFromFile(content.asDocument(), fileInputStream);
        } catch (IOException ioe) {
            response.addWarning("error detecting content type");
            LOGGER.warn("error detecting content type of file", ioe);
            return null;
        }

        return content;
    }
}
