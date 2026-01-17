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

import static io.undertow.util.Headers.ORIGIN;

import io.undertow.util.HttpString;

/**
 * Interface defining Cross-Origin Resource Sharing (CORS) headers for HTTP responses.
 * <p>
 * This interface provides methods for configuring CORS headers that allow web browsers
 * to make cross-origin requests to RESTHeart services. CORS is essential for web
 * applications running on different domains to interact with RESTHeart APIs.
 * </p>
 * <p>
 * The interface defines standard CORS headers including:
 * <ul>
 *   <li>Access-Control-Allow-Origin - specifies allowed origins</li>
 *   <li>Access-Control-Allow-Methods - specifies allowed HTTP methods</li>
 *   <li>Access-Control-Allow-Headers - specifies allowed request headers</li>
 *   <li>Access-Control-Allow-Credentials - controls credential sharing</li>
 *   <li>Access-Control-Expose-Headers - specifies exposed response headers</li>
 * </ul>
 * </p>
 * <p>
 * Default implementations are provided that work well for most use cases, but can
 * be overridden for specific security requirements or application needs.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface CORSHeaders {
    /** CORS header for specifying which response headers can be accessed by client-side scripts. */
    public static final HttpString ACCESS_CONTROL_EXPOSE_HEADERS = HttpString
            .tryFromString("Access-Control-Expose-Headers");

    /** CORS header for controlling whether credentials can be included in cross-origin requests. */
    public static final HttpString ACCESS_CONTROL_ALLOW_CREDENTIAL = HttpString
            .tryFromString("Access-Control-Allow-Credentials");

    /** CORS header for specifying which origins are allowed to access the resource. */
    public static final HttpString ACCESS_CONTROL_ALLOW_ORIGIN = HttpString
            .tryFromString("Access-Control-Allow-Origin");

    /** CORS header for specifying which HTTP methods are allowed for cross-origin requests. */
    public static final HttpString ACCESS_CONTROL_ALLOW_METHODS = HttpString
            .tryFromString("Access-Control-Allow-Methods");

    /** CORS header for specifying which request headers are allowed in cross-origin requests. */
    public static final HttpString ACCESS_CONTROL_ALLOW_HEADERS = HttpString
            .tryFromString("Access-Control-Allow-Headers");

    /**
     * Default list of response headers that are exposed to client-side scripts.
     * Includes location, etag, authentication token headers, and server identification.
     */
    public static final String DEFAULT_ACCESS_CONTROL_EXPOSE_HEADERS = "";

    /**
     * Returns the headers that should be exposed to client-side scripts.
     * <p>
     * These headers will be accessible via JavaScript in the browser for cross-origin
     * requests. The default implementation includes common RESTHeart headers like
     * Location, ETag, and authentication-related headers.
     * </p>
     *
     * @param r the request context for determining exposed headers
     * @return a comma-separated list of header names to expose to clients
     */
    default String accessControlExposeHeaders(Request<?> r) {
        return DEFAULT_ACCESS_CONTROL_EXPOSE_HEADERS;
    }

    /** Default setting for credential sharing in cross-origin requests (enabled). */
    public static final String DEFAULT_ACCESS_CONTROL_ALLOW_CREDENTIALS = "true";

    /**
     * Determines whether credentials (cookies, authorization headers, etc.) can be included
     * in cross-origin requests.
     * <p>
     * When set to "true", browsers will include credentials in cross-origin requests
     * and allow client-side scripts to access the response. This is typically needed
     * for authenticated API access from web applications.
     * </p>
     *
     * @param r the request context for determining credential policy
     * @return "true" to allow credentials, "false" to disallow them
     */
    default String accessControlAllowCredentials(Request<?> r) {
        return DEFAULT_ACCESS_CONTROL_ALLOW_CREDENTIALS;
    }

    /** Default allowed origin pattern (allows all origins). */
    public static final String DEFAULT_ACCESS_CONTROL_ALLOW_ORIGIN = "*";

    /**
     * Determines which origins are allowed to access the resource.
     * <p>
     * The default implementation checks for an Origin header in the request and
     * echoes it back, falling back to "*" (allow all origins) if no origin is present.
     * This approach provides flexibility while maintaining security for credentialed
     * requests.
     * </p>
     * <p>
     * For production environments, consider restricting this to specific trusted origins
     * rather than using the wildcard "*" pattern.
     * </p>
     *
     * @param r the request context containing origin information
     * @return the origin value to allow, either the request origin or "*"
     */
    default String accessControlAllowOrigin(Request<?> r) {
        var requestHeaders = r.getHeaders();
        if (requestHeaders.contains(ORIGIN)) {
            return requestHeaders.get(ORIGIN).getFirst();
        } else {
            return DEFAULT_ACCESS_CONTROL_ALLOW_ORIGIN;
        }
    }

    /** Default list of HTTP methods allowed for cross-origin requests. */
    public static final String DEFAULT_ACCESS_CONTROL_ALLOW_METHODS = "GET, PUT, POST, PATCH, DELETE";

    /**
     * Specifies which HTTP methods are allowed for cross-origin requests.
     * <p>
     * The default implementation allows all standard HTTP methods including
     * GET, PUT, POST, PATCH, DELETE.
     * </p>
     *
     * @param r the request context for determining allowed methods
     * @return a comma-separated list of allowed HTTP methods
     */
    default String accessControlAllowMethods(Request<?> r) {
        return DEFAULT_ACCESS_CONTROL_ALLOW_METHODS;
    }

    /**
     * Default list of request headers allowed in cross-origin requests.
     * Only includes headers that are NOT CORS-safelisted and require explicit permission.
     * Note: Content-Type is safelisted only for specific values (form data, text/plain).
     * For application/json (used by REST APIs), it must be explicitly allowed.
     * CORS-safelisted headers (Accept, Accept-Encoding, Accept-Language, etc.) are omitted as they're automatically allowed.
     */
    public static final String DEFAULT_ACCESS_CONTROL_ALLOW_HEADERS = "Authorization, Content-Type, X-Requested-With, No-Auth-Challenge";

    /**
     * Specifies which request headers are allowed in cross-origin requests.
     * <p>
     * The default implementation includes common HTTP headers needed for RESTful
     * operations, including authorization headers, content type specifications,
     * and RESTHeart-specific headers like No-Auth-Challenge.
     * </p>
     * <p>
     * This list should include any custom headers that client applications need
     * to send with their requests to ensure proper CORS handling.
     * </p>
     *
     * @param r the request context for determining allowed headers
     * @return a comma-separated list of allowed request header names
     */
    default String accessControlAllowHeaders(Request<?> r) {
        return DEFAULT_ACCESS_CONTROL_ALLOW_HEADERS;
    }
}
