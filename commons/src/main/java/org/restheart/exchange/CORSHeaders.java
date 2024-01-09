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

import static com.google.common.net.HttpHeaders.X_POWERED_BY;
import static io.undertow.util.Headers.ETAG;
import static io.undertow.util.Headers.LOCATION_STRING;
import static io.undertow.util.Headers.ORIGIN;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_HEADER;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_LOCATION_HEADER;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_VALID_HEADER;

import io.undertow.util.HttpString;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 *         Defines the CORS headers to be added to the response
 */
public interface CORSHeaders {
    public static final HttpString ACCESS_CONTROL_EXPOSE_HEADERS = HttpString
            .tryFromString("Access-Control-Expose-Headers");
    public static final HttpString ACCESS_CONTROL_ALLOW_CREDENTIAL = HttpString
            .tryFromString("Access-Control-Allow-Credentials");
    public static final HttpString ACCESS_CONTROL_ALLOW_ORIGIN = HttpString
            .tryFromString("Access-Control-Allow-Origin");
    public static final HttpString ACCESS_CONTROL_ALLOW_METHODS = HttpString
            .tryFromString("Access-Control-Allow-Methods");
    public static final HttpString ACCESS_CONTROL_ALLOW_HEADERS = HttpString
            .tryFromString("Access-Control-Allow-Headers");

    public static final String DEFAULT_ACCESS_CONTROL_EXPOSE_HEADERS = LOCATION_STRING
            + ", " + ETAG.toString()
            + ", " + AUTH_TOKEN_HEADER.toString()
            + ", " + AUTH_TOKEN_VALID_HEADER.toString()
            + ", " + AUTH_TOKEN_LOCATION_HEADER.toString()
            + ", " + X_POWERED_BY;

    /**
     * @return the values of the Access-Control-Expose-Headers
     */
    default String accessControlExposeHeaders(Request<?> r) {
        return DEFAULT_ACCESS_CONTROL_EXPOSE_HEADERS;
    }

    public static final String DEFAULT_ACCESS_CONTROL_ALLOW_CREDENTIALS = "true";

    /**
     * @return the values of the Access-Control-Allow-Credentials
     */
    default String accessControlAllowCredentials(Request<?> r) {
        return DEFAULT_ACCESS_CONTROL_ALLOW_CREDENTIALS;
    }

    public static final String DEFAULT_ACCESS_CONTROL_ALLOW_ORIGIN = "*";

    /**
     * @return the values of the Access-Control-Allow-Origin
     */
    default String accessControlAllowOrigin(Request<?> r) {
        var requestHeaders = r.getHeaders();
        if (requestHeaders.contains(ORIGIN)) {
            return requestHeaders.get(ORIGIN).getFirst().toString();
        } else {
            return DEFAULT_ACCESS_CONTROL_ALLOW_ORIGIN;
        }
    }

    public static final String DEFAULT_ACCESS_CONTROL_ALLOW_METHODS = "GET, PUT, POST, PATCH, DELETE, OPTIONS";

    /**
     * @return the values of the Access-Control-Allow-Methods
     */
    default String accessControlAllowMethods(Request<?> r) {
        return DEFAULT_ACCESS_CONTROL_ALLOW_METHODS;
    }

    public static final String DEFAULT_ACCESS_CONTROL_ALLOW_HEADERS = "Accept, Accept-Encoding, Authorization, "
            + "Content-Length, Content-Type, Host, If-Match, "
            + "Origin, X-Requested-With, User-Agent, No-Auth-Challenge";

    /**
     * @return the values of the Access-Control-Allow-Methods
     */
    default String accessControlAllowHeaders(Request<?> r) {
        return DEFAULT_ACCESS_CONTROL_ALLOW_HEADERS;
    }
}