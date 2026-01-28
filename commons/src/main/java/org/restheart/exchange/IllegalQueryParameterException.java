/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
 * Exception thrown when a query parameter is invalid or malformed.
 * <p>
 * This exception is used throughout the RESTHeart framework to signal that
 * a query parameter provided in an HTTP request is either syntactically
 * incorrect, semantically invalid, or not supported in the current context.
 * </p>
 * <p>
 * Common scenarios where this exception is thrown include:
 * <ul>
 *   <li>Invalid JSON syntax in filter or sort parameters</li>
 *   <li>Unsupported MongoDB operators in query expressions</li>
 *   <li>Malformed pagination parameters (negative page numbers, invalid page sizes)</li>
 *   <li>Invalid field names or projection syntax</li>
 *   <li>Incorrect data types for specific parameter values</li>
 * </ul>
 * </p>
 * <p>
 * This is a checked exception that requires explicit handling by callers,
 * ensuring that invalid query parameters are properly caught and converted
 * to appropriate HTTP error responses for clients.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class IllegalQueryParameterException extends Exception {

	private static final long serialVersionUID = 3012988294234123826L;

	/**
     * Constructs a new IllegalQueryParameterException with the specified detail message.
     * <p>
     * The detail message should clearly describe which query parameter is invalid
     * and why it's considered invalid. This message will typically be included
     * in the HTTP error response sent back to the client.
     * </p>
     *
     * @param message the detail message explaining the invalid query parameter
     */
    public IllegalQueryParameterException(String message) {
        super(message);
    }

    /**
     * Constructs a new IllegalQueryParameterException with the specified detail message and cause.
     * <p>
     * This constructor is useful when the query parameter validation fails due to
     * an underlying exception (such as JSON parsing errors, number format exceptions,
     * or MongoDB query validation failures). The cause provides additional context
     * about the root reason for the parameter validation failure.
     * </p>
     *
     * @param message the detail message explaining the invalid query parameter
     * @param cause the underlying cause of the parameter validation failure
     */
    public IllegalQueryParameterException(String message, Throwable cause) {
        super(message, cause);
    }
}
