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

import org.restheart.utils.HttpStatus;

/**
 * Exception thrown when a client request is malformed or contains invalid data.
 * <p>
 * This exception is used throughout the RESTHeart framework to signal various
 * types of client errors, typically resulting in HTTP 4xx status codes. It provides
 * flexibility in specifying custom status codes, message formats, and content types
 * for error responses.
 * </p>
 * <p>
 * The exception supports both plain text and JSON error messages, allowing services
 * to return structured error information when appropriate.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BadRequestException extends RuntimeException {
    /** Serial version UID for serialization compatibility. */
    private static final long serialVersionUID = -8466126772297299751L;

    /** The HTTP status code to be returned with this exception. */
    int statusCode = HttpStatus.SC_BAD_REQUEST;
    
    /** Flag indicating whether the exception message is valid JSON. */
    private final boolean jsonMessage;
    
    /** The content type to be used in the error response. */
    private final String contentType;

    /**
     * Creates a new BadRequestException with default settings.
     * <p>
     * Uses HTTP 400 (Bad Request) status code, plain text message format,
     * and JSON content type.
     * </p>
     */
    public BadRequestException() {
        super();
        this.jsonMessage = false;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     * Creates a new BadRequestException with a custom HTTP status code.
     * <p>
     * Uses plain text message format and JSON content type.
     * </p>
     *
     * @param statusCode the HTTP status code to return with this exception
     */
    public BadRequestException(int statusCode) {
        super();
        this.statusCode = statusCode;
        this.jsonMessage = false;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     * Creates a new BadRequestException with a custom error message.
     * <p>
     * Uses HTTP 400 (Bad Request) status code, plain text message format,
     * and JSON content type.
     * </p>
     *
     * @param message the error message to include in the exception
     */
    public BadRequestException(String message) {
        super(message);
        this.jsonMessage = false;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     * Creates a new BadRequestException with a custom error message and message format.
     * <p>
     * Uses HTTP 400 (Bad Request) status code and JSON content type.
     * </p>
     *
     * @param message the error message to include in the exception
     * @param jsonMessage true if the message is valid JSON, false for plain text
     */
    public BadRequestException(String message, boolean jsonMessage) {
        super(message);
        this.jsonMessage = jsonMessage;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     * Creates a new BadRequestException with custom message, format, and content type.
     * <p>
     * Uses HTTP 400 (Bad Request) status code.
     * </p>
     *
     * @param message the error message to include in the exception
     * @param jsonMessage true if the message is valid JSON, false for plain text
     * @param contentType the content type to use in the error response
     */
    public BadRequestException(String message, boolean jsonMessage, String contentType) {
        super(message);
        this.jsonMessage = jsonMessage;
        this.contentType = contentType;
    }

    /**
     * Creates a new BadRequestException with custom message and status code.
     * <p>
     * Uses plain text message format and JSON content type.
     * </p>
     *
     * @param message the error message to include in the exception
     * @param statusCode the HTTP status code to return with this exception
     */
    public BadRequestException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.jsonMessage = false;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     * Creates a new BadRequestException with custom message, status code, and message format.
     * <p>
     * Uses JSON content type.
     * </p>
     *
     * @param message the error message to include in the exception
     * @param statusCode the HTTP status code to return with this exception
     * @param jsonMessage true if the message is valid JSON, false for plain text
     */
    public BadRequestException(String message, int statusCode, boolean jsonMessage) {
        super(message);
        this.statusCode = statusCode;
        this.jsonMessage = jsonMessage;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     * Creates a new BadRequestException with full customization options.
     * <p>
     * This constructor provides complete control over all exception properties.
     * </p>
     *
     * @param message the error message to include in the exception
     * @param statusCode the HTTP status code to return with this exception
     * @param jsonMessage true if the message is valid JSON, false for plain text
     * @param contentType the content type to use in the error response
     */
    public BadRequestException(String message, int statusCode, boolean jsonMessage, String contentType) {
        super(message);
        this.statusCode = statusCode;
        this.jsonMessage = jsonMessage;
        this.contentType = contentType;
    }

    /**
     * Creates a new BadRequestException with a message and underlying cause.
     * <p>
     * Uses HTTP 400 (Bad Request) status code, plain text message format,
     * and plain text content type.
     * </p>
     *
     * @param message the error message to include in the exception
     * @param cause the underlying cause of this exception
     */
    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
        this.jsonMessage = false;
        this.contentType = Exchange.TEXT_PLAIN_CONTENT_TYPE;
    }

    /**
     * Creates a new BadRequestException with message, cause, and message format.
     * <p>
     * Uses HTTP 400 (Bad Request) status code and JSON content type.
     * </p>
     *
     * @param message the error message to include in the exception
     * @param jsonMessage true if the message is valid JSON, false for plain text
     * @param cause the underlying cause of this exception
     */
    public BadRequestException(String message, boolean jsonMessage, Throwable cause) {
        super(message, cause);
        this.jsonMessage = jsonMessage;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     * Creates a new BadRequestException with message, cause, format, and content type.
     * <p>
     * Uses HTTP 400 (Bad Request) status code.
     * </p>
     *
     * @param message the error message to include in the exception
     * @param jsonMessage true if the message is valid JSON, false for plain text
     * @param contentType the content type to use in the error response
     * @param cause the underlying cause of this exception
     */
    public BadRequestException(String message, boolean jsonMessage, String contentType, Throwable cause) {
        super(message, cause);
        this.jsonMessage = jsonMessage;
        this.contentType = contentType;
    }

    /**
     * Creates a new BadRequestException with message, status code, and cause.
     * <p>
     * Uses plain text message format and JSON content type.
     * </p>
     *
     * @param message the error message to include in the exception
     * @param statusCode the HTTP status code to return with this exception
     * @param cause the underlying cause of this exception
     */
    public BadRequestException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.jsonMessage = false;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     * Creates a new BadRequestException with message, status code, format, and cause.
     * <p>
     * Uses JSON content type.
     * </p>
     *
     * @param message the error message to include in the exception
     * @param statusCode the HTTP status code to return with this exception
     * @param jsonMessage true if the message is valid JSON, false for plain text
     * @param cause the underlying cause of this exception
     */
    public BadRequestException(String message, int statusCode, boolean jsonMessage, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.jsonMessage = jsonMessage;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     * Creates a new BadRequestException with full customization and a cause.
     * <p>
     * This constructor provides complete control over all exception properties
     * while also capturing the underlying cause of the error.
     * </p>
     *
     * @param message the error message to include in the exception
     * @param statusCode the HTTP status code to return with this exception
     * @param jsonMessage true if the message is valid JSON, false for plain text
     * @param contentType the content type to use in the error response
     * @param cause the underlying cause of this exception
     */
    public BadRequestException(String message, int statusCode, boolean jsonMessage, String contentType, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.jsonMessage = jsonMessage;
        this.contentType = contentType;
    }

    /**
     * Returns the HTTP status code associated with this exception.
     *
     * @return the HTTP status code to be returned in the error response
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Indicates whether the exception message is formatted as valid JSON.
     * <p>
     * When true, the message can be directly used as JSON content in the response.
     * When false, the message should be treated as plain text.
     * </p>
     *
     * @return true if the message is valid JSON, false otherwise
     */
    public boolean isJsonMessage() {
        return jsonMessage;
    }

    /**
     * Returns the content type to be used in the error response.
     * <p>
     * This determines how the client should interpret the error message content.
     * Common values include "application/json" and "text/plain".
     * </p>
     *
     * @return the content type for the error response
     */
    public String contentType() {
        return this.contentType;
    }
}
