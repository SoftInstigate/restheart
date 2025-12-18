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

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;

/**
 * The root class in the RESTHeart exchange hierarchy.
 * <p>
 * An exchange wraps Undertow's HttpServerExchange to provide simplified and unified
 * access to elements of HTTP requests and responses, such as query parameters, headers,
 * and content. This abstraction layer enables consistent handling of different content
 * types and processing scenarios across the RESTHeart framework.
 * </p>
 * <p>
 * The Exchange class serves as the foundation for all request and response handling
 * in RESTHeart, providing:
 * <ul>
 * <li>Content type detection and validation</li>
 * <li>Authentication and authorization status checking</li>
 * <li>Error state management</li>
 * <li>Response interceptor lifecycle management</li>
 * <li>Common utility methods for HTTP processing</li>
 * </ul>
 * </p>
 * <p>
 * The class hierarchy extends from Exchange to specialized request and response
 * implementations that handle specific content types (JSON, BSON, binary, etc.)
 * and processing scenarios (service, proxy, static resource).
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> the type of content handled by this exchange
 */
public abstract class Exchange<T> {

    /** MIME type for HAL+JSON (Hypertext Application Language) responses. */
    public static final String HAL_JSON_MEDIA_TYPE = "application/hal+json";

    /** MIME type for standard JSON content. */
    public static final String JSON_MEDIA_TYPE = "application/json";

    /** MIME type for XHTML content. */
    public static final String XHTML_MEDIA_TYPE = "application/xhtml+xml";

    /** MIME type for XML content. */
    public static final String XML_MEDIA_TYPE = "application/xml";

    /** MIME type for PDF documents. */
    public static final String APPLICATION_PDF_TYPE = "application/pdf";

    /** MIME type for plain text content. */
    public static final String TEXT_PLAIN_CONTENT_TYPE = "text/plain";

    /** MIME type for HTML content. */
    public static final String HTML_CONTENT_TYPE = "text/html";

    /** MIME type for XML content. */
    public static final String XML_CONTENT_TYPE = "text/xml";

    /** MIME type for URL-encoded form data. */
    public static final String FORM_URLENCODED = "application/x-www-form-urlencoded";

    /** MIME type for multipart form data (typically used for file uploads). */
    public static final String MULTIPART = "multipart/form-data";

    /** Attachment key for tracking error state in the HTTP exchange. */
    protected static final AttachmentKey<Boolean> IN_ERROR_KEY = AttachmentKey.create(Boolean.class);

    /** Attachment key for tracking whether response interceptors have been executed. */
    private static final AttachmentKey<Boolean> RESPONSE_INTERCEPTOR_EXECUTED = AttachmentKey.create(Boolean.class);

    /** Maximum content size allowed for request/response bodies (16 MB). */
    public static final int MAX_CONTENT_SIZE = 16 * 1024 * 1024; // 16Mbyte

    /** Maximum number of buffers used for content processing. */
    public static int MAX_BUFFERS = 1024;

    /**
     * Updates the maximum number of buffers based on the specified buffer size.
     * <p>
     * This method calculates the maximum number of buffers needed to handle
     * the maximum content size given a specific buffer size. It ensures that
     * the buffer pool can accommodate the largest possible content without
     * exceeding memory constraints.
     * </p>
     *
     * @param bufferSize the size of individual buffers in bytes
     */
    public static void updateBufferSize(int bufferSize) {
        MAX_BUFFERS = 1 + (MAX_CONTENT_SIZE / bufferSize);
    }

    /** The underlying Undertow HttpServerExchange that this Exchange wraps. */
    protected final HttpServerExchange wrapped;

    /**
     * Constructs a new Exchange wrapping the given HttpServerExchange.
     * <p>
     * This constructor is protected and should only be called by subclasses.
     * It initializes the exchange wrapper with the provided Undertow exchange
     * instance, enabling simplified access to HTTP request and response data.
     * </p>
     *
     * @param exchange the Undertow HttpServerExchange to wrap
     */
    public Exchange(HttpServerExchange exchange) {
        this.wrapped = exchange;
    }

    /**
     * Returns the underlying Undertow HttpServerExchange.
     * <p>
     * This method provides access to the wrapped Undertow exchange for
     * advanced operations that require direct interaction with the
     * underlying HTTP processing infrastructure.
     * </p>
     *
     * @return the wrapped HttpServerExchange instance
     */
    protected HttpServerExchange getWrappedExchange() {
        return wrapped;
    }

    /**
     * Checks if the specified HTTP exchange is in an error state.
     * <p>
     * This static utility method examines the exchange's attachments to determine
     * if an error condition has been flagged during request processing. It's
     * commonly used by interceptors and handlers to check error status.
     * </p>
     *
     * @param exchange the HttpServerExchange to check
     * @return true if the exchange is in an error state, false otherwise
     */
    public static boolean isInError(HttpServerExchange exchange) {
        return exchange.getAttachment(IN_ERROR_KEY) != null && exchange.getAttachment(IN_ERROR_KEY);
    }

    /**
     * Checks if the specified HTTP exchange represents an authenticated request.
     * <p>
     * This static utility method examines the exchange's security context to
     * determine if the request has been successfully authenticated. It checks
     * both for the presence of a security context and an authenticated account.
     * </p>
     *
     * @param exchange the HttpServerExchange to check
     * @return true if the exchange represents an authenticated request, false otherwise
     */
    public static boolean isAuthenticated(HttpServerExchange exchange) {
        return exchange.getSecurityContext() != null && exchange.getSecurityContext().getAuthenticatedAccount() != null;
    }

    /**
     * Marks the specified HTTP exchange as being in an error state.
     * <p>
     * This static utility method sets the error flag on the exchange, indicating
     * that an error condition has occurred during request processing. This flag
     * can be checked by subsequent handlers and interceptors to modify their behavior.
     * </p>
     *
     * @param exchange the HttpServerExchange to mark as errored
     */
    public static void setInError(HttpServerExchange exchange) {
        exchange.putAttachment(IN_ERROR_KEY, true);
    }

    /**
     * Checks if response interceptors have been executed for the specified exchange.
     * <p>
     * This method examines the exchange's attachments to determine if the response
     * interceptor pipeline has already been executed. This prevents duplicate
     * execution of response interceptors in complex processing scenarios.
     * </p>
     *
     * @param exchange the HttpServerExchange to check
     * @return true if response interceptors have been executed, false otherwise
     */
    public static boolean responseInterceptorsExecuted(HttpServerExchange exchange) {
        return exchange.getAttachment(RESPONSE_INTERCEPTOR_EXECUTED) != null
                && exchange.getAttachment(RESPONSE_INTERCEPTOR_EXECUTED);
    }

    /**
     * Marks that response interceptors have been executed for the specified exchange.
     * <p>
     * This method sets a flag indicating that the response interceptor pipeline
     * has been executed for the exchange. This prevents duplicate execution of
     * response interceptors and ensures proper lifecycle management.
     * </p>
     *
     * @param exchange the HttpServerExchange to mark as having executed response interceptors
     */
    public static void setResponseInterceptorsExecuted(HttpServerExchange exchange) {
        exchange.putAttachment(RESPONSE_INTERCEPTOR_EXECUTED, true);
    }

    /**
     * Returns the underlying Undertow HttpServerExchange.
     * <p>
     * This method provides public access to the wrapped Undertow exchange,
     * allowing external components to interact directly with the underlying
     * HTTP processing infrastructure when necessary.
     * </p>
     *
     * @return the wrapped HttpServerExchange instance
     */
    public HttpServerExchange getExchange() {
        return wrapped;
    }

    /**
     * Returns the content type of this exchange.
     * <p>
     * This abstract method must be implemented by subclasses to provide
     * access to the appropriate content type, whether from request or
     * response headers depending on the exchange type.
     * </p>
     *
     * @return the content type string, or null if not specified
     */
    public abstract String getContentType();

    /**
     * Checks if the content type is JSON.
     * <p>
     * This helper method determines if the content type is application/json
     * or starts with application/json (allowing for additional parameters
     * like charset specifications).
     * </p>
     *
     * @return true if the content type is JSON, false otherwise
     */
    public boolean isContentTypeJson() {
        return "application/json".equals(getContentType())
                || (getContentType() != null && getContentType().startsWith("application/json;"));
    }

    /**
     * Checks if this exchange is in an error state.
     * <p>
     * This method examines the exchange's error flag to determine if an
     * error condition has been flagged during processing. It provides
     * instance-level access to the error state.
     * </p>
     *
     * @return true if the exchange is in an error state, false otherwise
     */
    public boolean isInError() {
        return getWrappedExchange().getAttachment(IN_ERROR_KEY) != null
                && (boolean) getWrappedExchange().getAttachment(IN_ERROR_KEY);
    }

    /**
     * Sets the error state for this exchange.
     * <p>
     * This method allows setting or clearing the error flag for the exchange.
     * When set to true, it indicates that an error condition has occurred
     * during processing. When set to false, it clears any previous error state.
     * </p>
     *
     * @param inError true to mark the exchange as errored, false to clear error state
     */
    public void setInError(boolean inError) {
        getWrappedExchange().putAttachment(IN_ERROR_KEY, inError);
    }

    /**
     * Checks if the content type is XML.
     * <p>
     * This helper method determines if the content type is XML, checking for
     * both text/xml and application/xml MIME types, with or without additional
     * parameters like charset specifications.
     * </p>
     *
     * @return true if the content type is XML, false otherwise
     */
    public boolean isContentTypeXml() {
        return "text/xml".equals(getContentType())
                || (getContentType() != null && getContentType().startsWith("text/xml;"))
                || "application/xml".equals(getContentType())
                || (getContentType() != null && getContentType().startsWith("application/xml;"));
    }

    /**
     * Checks if the content type is text-based.
     * <p>
     * This helper method determines if the content type indicates text content
     * by checking if it starts with "text/". This includes text/plain, text/html,
     * text/xml, and other text-based MIME types.
     * </p>
     *
     * @return true if the content type is text-based, false otherwise
     */
    public boolean isContentTypeText() {
        return getContentType() != null && getContentType().startsWith("text/");
    }

    /**
     * Checks if the request content type is JSON for the specified exchange.
     * <p>
     * This static utility method examines the Content-Type header of the
     * request to determine if it indicates JSON content. It checks for
     * exact matches and content types that start with application/json.
     * </p>
     *
     * @param exchange the HttpServerExchange to check
     * @return true if the request content type is JSON, false otherwise
     */
    public static boolean isContentTypeJson(HttpServerExchange exchange) {
        var ct = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        return "application/json".equals(ct) || (ct != null && ct.startsWith("application/json;"));
    }

    /**
     * Checks if the request content type is form-encoded or multipart for the specified exchange.
     * <p>
     * This static utility method examines the Content-Type header to determine
     * if it indicates form data (either URL-encoded or multipart). This is
     * commonly used to identify file uploads or form submissions.
     * </p>
     *
     * @param exchange the HttpServerExchange to check
     * @return true if the request content type is form-encoded or multipart, false otherwise
     */
    public static boolean isContentTypeFormOrMultipart(HttpServerExchange exchange) {
        var ct = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        return ct != null && (ct.startsWith(FORM_URLENCODED) || ct.startsWith(MULTIPART));
    }

    /**
     * Checks if the content type is form-encoded or multipart for this exchange.
     * <p>
     * This instance method provides convenient access to form content type
     * checking for the current exchange, delegating to the static utility method.
     * </p>
     *
     * @return true if the content type is form-encoded or multipart, false otherwise
     */
    public boolean isContentTypeFormOrMultipart() {
        return isContentTypeFormOrMultipart(wrapped);
    }

    /**
     * Checks if the content type is PDF.
     * <p>
     * This helper method determines if the content type is application/pdf
     * or starts with application/pdf (allowing for additional parameters
     * like charset specifications).
     * </p>
     *
     * @return true if the content type is PDF, false otherwise
     */
    public boolean isContentTypePdf() {
        return APPLICATION_PDF_TYPE.equals(getContentType())
                || (getContentType() != null && getContentType().startsWith(APPLICATION_PDF_TYPE + ";"));
    }

    /**
     * Checks if the content type is HTML.
     * <p>
     * This helper method determines if the content type is text/html
     * or starts with text/html (allowing for additional parameters
     * like charset specifications).
     * </p>
     *
     * @return true if the content type is HTML, false otherwise
     */
    public boolean isContentTypeHtml() {
        return "text/html".equals(getContentType())
                || (getContentType() != null && getContentType().startsWith("text/html;"));
    }

    /**
     * Checks if the content type is XHTML.
     * <p>
     * This helper method determines if the content type is application/xhtml+xml
     * or starts with application/xhtml+xml (allowing for additional parameters
     * like charset specifications).
     * </p>
     *
     * @return true if the content type is XHTML, false otherwise
     */
    public boolean isContentTypeXhtml() {
        return "application/xhtml+xml".equals(getContentType())
                || (getContentType() != null && getContentType().startsWith("application/xhtml+xml;"));
    }

    /**
     * Checks if the content type is HAL+JSON.
     * <p>
     * This helper method determines if the content type is application/hal+json
     * or starts with application/hal+json (allowing for additional parameters
     * like charset specifications).
     * </p>
     *
     * @return true if the content type is HAL+JSON, false otherwise
     */
    public boolean isContentTypeHalJson() {
        return HAL_JSON_MEDIA_TYPE.equals(getContentType())
                || (getContentType() != null && getContentType().startsWith(HAL_JSON_MEDIA_TYPE + ";"));
    }

    /**
     * Checks if the content type is text/plain.
     * <p>
     * This helper method determines if the content type is text/plain
     * or starts with text/plain (allowing for additional parameters
     * like charset specifications).
     * </p>
     *
     * @return true if the content type is text/plain, false otherwise
     */
    public boolean isContentTypeTextPlain() {
        return TEXT_PLAIN_CONTENT_TYPE.equals(getContentType())
                || (getContentType() != null && getContentType().startsWith(TEXT_PLAIN_CONTENT_TYPE + ";"));
    }

    /**
     * Checks if the content type is application/octet-stream.
     * <p>
     * This helper method determines if the content type is application/octet-stream
     * or starts with application/octet-stream (allowing for additional parameters
     * like charset specifications).
     * </p>
     *
     * @return true if the content type is application/octet-stream, false otherwise
     */
    public boolean isContentTypeApplicationOctetStream() {
        return "application/octet-stream".equals(getContentType())
                || (getContentType() != null && getContentType().startsWith("application/octet-stream;"));
    }

    /**
     * Checks if the content type is application/x-www-form-urlencoded.
     * <p>
     * This helper method determines if the content type is application/x-www-form-urlencoded
     * or starts with application/x-www-form-urlencoded (allowing for additional parameters
     * like charset specifications).
     * </p>
     *
     * @return true if the content type is application/x-www-form-urlencoded, false otherwise
     */
    public boolean isContentTypeApplicationXWwwFormUrlEncoded() {
        return FORM_URLENCODED.equals(getContentType())
                || (getContentType() != null && getContentType().startsWith(FORM_URLENCODED + ";"));
    }

    /**
     * Checks if this exchange represents an authenticated request.
     * <p>
     * This method examines the security context to determine if the request
     * has been successfully authenticated. It provides instance-level access
     * to authentication status checking.
     * </p>
     *
     * @return true if the request is authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        return getWrappedExchange().getSecurityContext() != null
                && getWrappedExchange().getSecurityContext().getAuthenticatedAccount() != null;
    }

    /**
     * Checks if the authenticated account has the specified role.
     * <p>
     * This method determines if the currently authenticated account (if any)
     * has been assigned the specified role. It returns false if the request
     * is not authenticated, if no roles are assigned, or if the specified
     * role is not among the account's roles.
     * </p>
     *
     * @param role the role name to check for
     * @return true if the authenticated account has the specified role, false otherwise
     */
    public boolean isAccountInRole(String role) {
        if (!isAuthenticated()) {
            return false;
        } else if (getWrappedExchange().getSecurityContext().getAuthenticatedAccount().getRoles() == null) {
            return false;
        } else {
            return getWrappedExchange().getSecurityContext().getAuthenticatedAccount().getRoles().contains(role);
        }
    }
}
