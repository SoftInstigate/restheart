/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
import static org.restheart.exchange.ExchangeKeys.FILE_METADATA;
import static org.restheart.exchange.ExchangeKeys.PROPERTIES;
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
 * Utility class for injecting and processing request content for MongoDB operations.
 * <p>
 * This class provides static methods for parsing and validating HTTP request content
 * specifically for MongoDB operations through RESTHeart. It handles various content
 * types including JSON, form data, and multipart uploads, ensuring proper content
 * injection into MongoRequest instances.
 * </p>
 * <p>
 * The injector supports multiple content types:
 * <ul>
 *   <li><strong>application/json</strong> and <strong>application/hal+json</strong> - for JSON document operations</li>
 *   <li><strong>application/x-www-form-urlencoded</strong> - for form-based document creation</li>
 *   <li><strong>multipart/form-data</strong> - for file uploads with metadata</li>
 * </ul>
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Content type validation and appropriate parser selection</li>
 *   <li>JSON document validation and BSON conversion</li>
 *   <li>GridFS file upload handling with metadata extraction</li>
 *   <li>Reserved document ID validation</li>
 *   <li>Update operator validation for POST/PUT operations</li>
 *   <li>Form data parsing and JSON field conversion</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoRequestContentInjector {
    static final Logger LOGGER = LoggerFactory.getLogger(MongoRequestContentInjector.class);

    private static final String ERROR_INVALID_CONTENTTYPE = "Content-Type must be either: " + Exchange.JSON_MEDIA_TYPE + " or " + Exchange.HAL_JSON_MEDIA_TYPE;

    /**
     * Checks if the content type is JSON or HAL+JSON.
     * <p>
     * This method validates that the Content-Type header indicates JSON content
     * that can be parsed by the JSON content injector. It accepts both standard
     * JSON and HAL+JSON formats, as well as requests with no content type specified.
     * </p>
     *
     * @param contentTypes the Content-Type header values from the request
     * @return true if the content type is JSON, HAL+JSON, or not specified; false otherwise
     */
    private static boolean isHalOrJson(final HeaderValues contentTypes) {
        return (contentTypes == null
                || contentTypes.isEmpty())
                || (contentTypes.stream().anyMatch(ct -> ct.startsWith(Exchange.HAL_JSON_MEDIA_TYPE)
                || ct.startsWith(Exchange.JSON_MEDIA_TYPE)));
    }

    /**
     * Checks if the content type is form-encoded or multipart.
     * <p>
     * This method validates that the Content-Type header indicates form data
     * that requires special parsing (either URL-encoded forms or multipart uploads).
     * This is typically used for file uploads or form-based document creation.
     * </p>
     *
     * @param contentTypes the Content-Type header values from the request
     * @return true if the content type is form-encoded or multipart; false otherwise
     */
    private static boolean isFormOrMultipart(final HeaderValues contentTypes) {
        return contentTypes != null
                && !contentTypes.isEmpty()
                && contentTypes.stream().anyMatch(ct -> ct.startsWith(Exchange.FORM_URLENCODED) || ct.startsWith(Exchange.MULTIPART));
    }

    /**
     * Extracts metadata from multipart form data for file upload operations.
     * <p>
     * This method searches the form data for a field named 'metadata' or 'properties'
     * which should contain valid JSON describing the file's metadata. This is typically
     * used in GridFS file uploads where metadata needs to be associated with the file.
     * </p>
     * <p>
     * The metadata field is parsed as JSON and converted to a BsonDocument. If no
     * metadata field is present, an empty BsonDocument is returned. If the metadata
     * field contains invalid JSON or is not a JSON object, a BadRequestException is thrown.
     * </p>
     *
     * @param formData the parsed multipart form data from the request
     * @return the parsed BsonDocument containing file metadata, or an empty BsonDocument if no metadata provided
     * @throws BadRequestException if the metadata field contains invalid JSON or is not a JSON object
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
     * Finds the name of the first file field in multipart form data.
     * <p>
     * This method iterates through the form data fields to locate the first field
     * that contains a file upload. This is used in GridFS operations where the
     * actual file content needs to be identified among other form fields.
     * </p>
     *
     * @param formData the parsed multipart form data from the request
     * @return the name of the first file field found, or null if no file fields are present
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
     * Injects request content into a MongoRequest based on the Content-Type header.
     * <p>
     * This is the main entry point for content injection. It analyzes the request's
     * Content-Type header and delegates to the appropriate parsing method:
     * </p>
     * <ul>
     *   <li>JSON/HAL+JSON content is parsed by {@link #injectBson(HttpServerExchange)}</li>
     *   <li>Form/multipart content is parsed by {@link #injectMultipart(HttpServerExchange, MongoRequest, MongoResponse)}</li>
     *   <li>No content type defaults to JSON parsing</li>
     * </ul>
     * <p>
     * The method also performs validation:
     * <ul>
     *   <li>Ensures array content is only used in appropriate contexts</li>
     *   <li>Validates document ID types and reserved ID values</li>
     *   <li>Prohibits update operators in POST/PUT requests</li>
     *   <li>Unflattens dot notation for POST/PUT operations</li>
     * </ul>
     * </p>
     *
     * @param exchange the HTTP server exchange containing the request
     * @return the parsed BSON content as a BsonValue, or an empty BsonDocument if no content
     * @throws BadRequestException if the content is invalid, has wrong content type, or violates MongoDB rules
     * @throws IOException if there is an error reading the request content
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

            if (content.asArray().stream().noneMatch(_doc -> {
                if (_doc.isDocument()) {
                    var _id = _doc.asDocument().get(_ID);

                    if (_id != null && _id.isArray()) {
                        throw new BadRequestException("the type of _id in request data is not supported: " + _id.getBsonType().name());
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

    /**
     * Parses JSON content from the request body into BSON format.
     * <p>
     * This method handles the parsing of JSON content (application/json or application/hal+json)
     * from the request body. It first checks if content has already been buffered by a proxy
     * request, and if not, reads it directly from the request channel.
     * </p>
     * <p>
     * The method validates that the parsed content is either a JSON object or array,
     * as primitive values are not supported for MongoDB document operations.
     * </p>
     *
     * @param exchange the HTTP server exchange containing the JSON request
     * @return the parsed JSON content as a BsonValue, or null if no content is present
     * @throws BadRequestException if the JSON is malformed or contains unsupported data types
     * @throws IOException if there is an error reading the request content
     */
    private static BsonValue injectBson(HttpServerExchange exchange) throws BadRequestException, IOException {
        BsonValue content;
        final String rawBody;

        var bar = ByteArrayProxyRequest.of(exchange);

        if (bar.isContentAvailable()) {
            // if content has been already injected
            // get it from MongoRequest.readContent()
            rawBody = new String(bar.readContent(), StandardCharsets.UTF_8);
        } else {
            // otherwise use ChannelReader
            rawBody = ChannelReader.readString(exchange);
        }

        MongoRequest.of(exchange).setRawBody(rawBody);

        // parse the json content
        if (rawBody != null && !rawBody.isEmpty()) { // check content type
            try {
                content = BsonUtils.parse(rawBody);

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

    /**
     * Creates a form data parser for the given HTTP exchange.
     * <p>
     * This method creates a FormDataParser configured with UTF-8 encoding for processing
     * multipart form data or URL-encoded form data. The parser requires the exchange to
     * be in blocking mode, which should be handled by the WorkingThreadsPoolDispatcher.
     * </p>
     *
     * @param exchange the HTTP server exchange to create a parser for
     * @return a FormDataParser instance configured for the exchange, or null if no suitable parser exists
     */
    private static FormDataParser parser(HttpServerExchange exchange) {
        // form data requires exchange.startBlocking(); called by WorkingThreadsPoolDispatcher

        return FORM_PARSER.createParser(exchange);
    }

    /**
     * Processes multipart form data for document operations or file uploads.
     * <p>
     * This method handles multipart form data by determining if it's a file upload operation
     * (for GridFS) or a regular document operation with form fields. For file operations,
     * it delegates to {@link #injectMultipartForFiles(HttpServerExchange, MongoRequest, MongoResponse)}.
     * For regular operations, it processes form fields and attempts to parse them as JSON.
     * </p>
     * <p>
     * Each form field is processed as follows:
     * <ul>
     *   <li>File fields are ignored in non-file operations</li>
     *   <li>Empty values become BsonNull</li>
     *   <li>Blank strings remain as BsonString</li>
     *   <li>Other values are parsed as JSON, falling back to strings if parsing fails</li>
     * </ul>
     * </p>
     *
     * @param exchange the HTTP server exchange containing the multipart request
     * @param request the MongoRequest being processed
     * @param response the MongoResponse for error handling
     * @return a BsonDocument containing the processed form fields, or the result of file processing
     * @throws BadRequestException if no form parser is available or JSON parsing fails
     * @throws IOException if there is an error reading the form data
     */
    private static BsonValue injectMultipart(HttpServerExchange exchange, MongoRequest request, MongoResponse response) throws BadRequestException, IOException {
        // form data requires exchange.startBlocking(); called by WorkingThreadsPoolDispatcher

        if (request.isWriteDocument() && (request.isFile() || request.isFilesBucket())) {
             return injectMultipartForFiles(exchange, request, response);
        }

        var parser = parser(exchange);

        if (parser == null) {
            throw new BadRequestException("There is no form parser registered for the request content type", HttpStatus.SC_BAD_REQUEST);
        }

        var formData = parser.parseBlocking();

        var ret = new BsonDocument();
        boolean[] errored = {false};

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

    /**
     * Processes multipart form data specifically for GridFS file upload operations.
     * <p>
     * This method handles file uploads by extracting both the file content and associated
     * metadata from the multipart form data. The metadata is extracted from a 'metadata'
     * or 'properties' field and must be valid JSON. The actual file content is identified
     * as the first file field in the form data.
     * </p>
     * <p>
     * The method sets up the request for file processing by:
     * <ul>
     *   <li>Parsing and validating the metadata JSON</li>
     *   <li>Locating the file field in the form data</li>
     *   <li>Setting the file input stream on the MongoRequest for later processing</li>
     * </ul>
     * </p>
     *
     * @param exchange the HTTP server exchange containing the multipart file upload
     * @param request the MongoRequest to configure with file input stream
     * @param response the MongoResponse for warning messages
     * @return a BsonDocument containing the parsed file metadata
     * @throws BadRequestException if no form parser is available, metadata is invalid, or no file is present
     * @throws IOException if there is an error accessing the file input stream
     */
    private static BsonValue injectMultipartForFiles(HttpServerExchange exchange, MongoRequest request, MongoResponse response)  throws BadRequestException, IOException {
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
