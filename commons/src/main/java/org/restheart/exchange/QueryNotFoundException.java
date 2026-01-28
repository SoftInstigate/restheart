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
 * Exception thrown when a requested query operation cannot be found or executed.
 * <p>
 * This exception is used throughout the RESTHeart framework to signal that
 * a specific query, aggregation, or database operation that was requested
 * cannot be located or executed. This typically occurs when referencing
 * predefined queries, aggregations, or operations that don't exist in the
 * current configuration.
 * </p>
 * <p>
 * Common scenarios where this exception is thrown include:
 * <ul>
 *   <li>Attempting to execute a predefined aggregation that doesn't exist</li>
 *   <li>Referencing a named query that hasn't been configured</li>
 *   <li>Trying to access a parameterized operation that's not available</li>
 *   <li>Requesting a stored procedure or function that cannot be found</li>
 *   <li>Accessing collection-specific operations that are not defined</li>
 * </ul>
 * </p>
 * <p>
 * This is a checked exception that requires explicit handling by callers,
 * ensuring that missing query operations are properly caught and converted
 * to appropriate HTTP error responses for clients. The exception typically
 * results in a 404 Not Found response when thrown during query processing.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class QueryNotFoundException extends Exception {

    /** Serial version UID for serialization compatibility. */
    private static final long serialVersionUID = 679969839186279828L;

    /**
     * Constructs a new QueryNotFoundException with no detail message.
     * <p>
     * This constructor creates an exception without a specific error message.
     * While functional, it's generally better to use the constructors that
     * accept a message parameter to provide more context about which query
     * or operation could not be found.
     * </p>
     */
    public QueryNotFoundException() {
        super();
    }

    /**
     * Constructs a new QueryNotFoundException with the specified detail message.
     * <p>
     * The detail message should clearly describe which query, aggregation, or
     * operation could not be found. This message will typically be included
     * in the HTTP error response sent back to the client, so it should be
     * informative but not expose internal system details.
     * </p>
     *
     * @param message the detail message explaining which query was not found
     */
    public QueryNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new QueryNotFoundException with the specified detail message and cause.
     * <p>
     * This constructor is useful when the query lookup fails due to an underlying
     * exception (such as configuration parsing errors, database connection issues,
     * or resource loading problems). The cause provides additional context about
     * the root reason for the query lookup failure.
     * </p>
     *
     * @param message the detail message explaining which query was not found
     * @param cause the underlying cause of the query lookup failure
     */
    public QueryNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
