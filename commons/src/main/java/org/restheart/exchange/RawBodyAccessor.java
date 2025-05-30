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

/**
 * Interface for accessing the raw, unparsed body content of HTTP requests.
 * <p>
 * This interface provides a contract for request classes that need to maintain
 * access to the original, unprocessed body content alongside their parsed
 * representations. This is particularly useful for scenarios where both the
 * parsed content and the original raw content are needed.
 * </p>
 * <p>
 * Common use cases include:
 * <ul>
 *   <li>Logging or auditing of original request content</li>
 *   <li>Content validation that requires access to the raw format</li>
 *   <li>Forwarding original content to backend services in proxy scenarios</li>
 *   <li>Digital signature verification that depends on exact byte sequences</li>
 *   <li>Debugging and troubleshooting request processing issues</li>
 * </ul>
 * </p>
 * <p>
 * Implementations should ensure that the raw body is preserved during the
 * parsing process and remains accessible throughout the request lifecycle.
 * The raw body is typically captured when the content is first read from
 * the request stream, before any parsing or transformation occurs.
 * </p>
 * 
 * @param <T> the type of the raw body representation (typically String or byte[])
 */
interface RawBodyAccessor<T> {

    /**
     * Returns the raw, unparsed body content of the HTTP request.
     * <p>
     * This method provides access to the original request body content as it
     * was received, before any parsing, transformation, or processing occurred.
     * The returned content preserves the exact format and encoding of the
     * original request.
     * </p>
     * <p>
     * The raw body is typically available after the request content has been
     * read and parsed. If the request has no body content, this method may
     * return null or an empty representation depending on the implementation.
     * </p>
     * <p>
     * Note: The availability of raw body content depends on the request
     * processing pipeline. Some request types may not preserve the raw body
     * if it's not needed for their specific use case.
     * </p>
     *
     * @return the request's raw body content as type T, or null if no raw body is available
     */
    T getRawBody();

}
