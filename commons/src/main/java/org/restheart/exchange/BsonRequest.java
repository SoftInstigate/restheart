/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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

import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.ChannelReader;

import io.undertow.server.HttpServerExchange;

/**
 * ServiceRequest implementation that handles BSON (Binary JSON) content.
 * <p>
 * This class provides a specialized request handler for processing JSON content
 * that gets parsed into BSON format for MongoDB operations. It extends ServiceRequest
 * and implements RawBodyAccessor to provide access to both the parsed BSON content
 * and the original raw JSON string.
 * </p>
 * <p>
 * The class automatically parses incoming JSON content from the request body into
 * BsonValue objects using the RESTHeart BsonUtils parser. If the JSON is malformed,
 * a BadRequestException is thrown with details about the parsing error.
 * </p>
 * <p>
 * This request type is commonly used by MongoDB services that need to process
 * JSON documents, queries, or other BSON-compatible data structures.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BsonRequest extends ServiceRequest<BsonValue> implements RawBodyAccessor<String> {

    /** The raw JSON content from the request body before BSON parsing. */
    private String rawBody;

    /**
     * Constructs a new BsonRequest wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by factory methods
     * or subclasses. Use {@link #init(HttpServerExchange)} or {@link #of(HttpServerExchange)}
     * to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected BsonRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Factory method to create a new BsonRequest instance.
     * <p>
     * This method creates a fresh BsonRequest instance for the given exchange.
     * The content will be parsed when {@link #parseContent()} is called.
     * </p>
     *
     * @param exchange the HTTP server exchange containing the JSON request
     * @return a new BsonRequest instance
     */
    public static BsonRequest init(HttpServerExchange exchange) {
        return new BsonRequest(exchange);
    }

    /**
     * Factory method to retrieve or create a BsonRequest from an existing exchange.
     * <p>
     * This method retrieves an existing BsonRequest instance that has been
     * previously attached to the exchange, or creates a new one if none exists.
     * </p>
     *
     * @param exchange the HTTP server exchange
     * @return the BsonRequest associated with the exchange
     */
    public static BsonRequest of(HttpServerExchange exchange) {
        return of(exchange, BsonRequest.class);
    }

    /**
     * Parses the JSON content from the request body into a BsonValue.
     * <p>
     * This method reads the raw JSON content from the request body and converts
     * it into a BsonValue using RESTHeart's BsonUtils parser. The raw content
     * is also stored internally for access via {@link #getRawBody()}.
     * </p>
     * <p>
     * The parsing process supports all valid JSON structures including:
     * <ul>
     *   <li>JSON objects (converted to BsonDocument)</li>
     *   <li>JSON arrays (converted to BsonArray)</li>
     *   <li>Primitive values (strings, numbers, booleans, null)</li>
     *   <li>MongoDB extended JSON format</li>
     * </ul>
     * </p>
     *
     * @return the parsed BSON representation of the request content
     * @throws IOException if there is an error reading the request body
     * @throws BadRequestException if the JSON content is malformed or cannot be parsed
     */
    @Override
    public BsonValue parseContent() throws IOException, BadRequestException {
        try {
            rawBody = ChannelReader.readString(wrapped);
            return BsonUtils.parse(rawBody);
        } catch (JsonParseException jpe) {
            throw new BadRequestException(jpe.getMessage(), jpe);
        }
    }

    /**
     * Returns the raw JSON content from the request body before BSON parsing.
     * <p>
     * This method provides access to the original JSON string that was received
     * in the request body. The raw content is available after {@link #parseContent()}
     * has been called. This can be useful for logging, debugging, or when the
     * original format needs to be preserved.
     * </p>
     *
     * @return the raw JSON content as a string, or null if content hasn't been parsed yet
     */
    @Override
    public final String getRawBody() {
        return rawBody;
    }
}
