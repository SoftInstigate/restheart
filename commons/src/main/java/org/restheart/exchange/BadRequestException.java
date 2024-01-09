/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BadRequestException extends RuntimeException {
    /**
     *
     */
    private static final long serialVersionUID = -8466126772297299751L;

    int statusCode = HttpStatus.SC_BAD_REQUEST;
    private final boolean jsonMessage;
    private final String contentType;

    /**
     *
     */
    public BadRequestException() {
        super();
        this.jsonMessage = false;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     *
     * @param statusCode
     */
    public BadRequestException(int statusCode) {
        super();
        this.statusCode = statusCode;
        this.jsonMessage = false;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     *
     * @param message
     */
    public BadRequestException(String message) {
        super(message);
        this.jsonMessage = false;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     *
     * @param message
     * @param jsonMessage mark message as json
     */
    public BadRequestException(String message, boolean jsonMessage) {
        super(message);
        this.jsonMessage = jsonMessage;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     *
     * @param message
     * @param jsonMessage mark message as json
     * @param contentType error response content type
     */
    public BadRequestException(String message, boolean jsonMessage, String contentType) {
        super(message);
        this.jsonMessage = jsonMessage;
        this.contentType = contentType;
    }

    /**
     *
     * @param message
     * @param statusCode
     */
    public BadRequestException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.jsonMessage = false;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     *
     * @param message
     * @param statusCode
     * @param jsonMessage mark message as json
     */
    public BadRequestException(String message, int statusCode, boolean jsonMessage) {
        super(message);
        this.statusCode = statusCode;
        this.jsonMessage = jsonMessage;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     *
     * @param message
     * @param statusCode
     * @param jsonMessage mark message as json
     * @param contentType error response content type
     */
    public BadRequestException(String message, int statusCode, boolean jsonMessage, String contentType) {
        super(message);
        this.statusCode = statusCode;
        this.jsonMessage = jsonMessage;
        this.contentType = contentType;
    }

    /**
     *
     * @param message
     * @param cause
     */
    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
        this.jsonMessage = false;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     *
     * @param message
     * @param cause
     * @param jsonMessage mark message as json
     */
    public BadRequestException(String message, boolean jsonMessage, Throwable cause) {
        super(message, cause);
        this.jsonMessage = jsonMessage;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     *
     * @param message
     * @param cause
     * @param jsonMessage mark message as json
     * @param contentType error response content type
     */
    public BadRequestException(String message, boolean jsonMessage, String contentType, Throwable cause) {
        super(message, cause);
        this.jsonMessage = jsonMessage;
        this.contentType = contentType;
    }

    /**
     *
     * @param message
     * @param statusCode
     * @param cause
     */
    public BadRequestException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.jsonMessage = false;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     *
     * @param message
     * @param statusCode
     * @param jsonMessage mark message as json
     * @param cause
     */
    public BadRequestException(String message, int statusCode, boolean jsonMessage, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.jsonMessage = jsonMessage;
        this.contentType = Exchange.JSON_MEDIA_TYPE;
    }

    /**
     *
     * @param message
     * @param statusCode
     * @param jsonMessage mark message as json
     * @param contentType error response content type
     * @param cause
     */
    public BadRequestException(String message, int statusCode, boolean jsonMessage, String contentType, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.jsonMessage = jsonMessage;
        this.contentType = contentType;
    }

    /**
     * @return the statusCode
     */
    public int getStatusCode() {
        return statusCode;
    }

    public boolean isJsonMessage() {
        return jsonMessage;
    }

    public String contentType() {
        return this.contentType;
    }
}
