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
 * Exception thrown when a query or aggregation variable is referenced but not bound to a value.
 * <p>
 * This exception is used throughout the RESTHeart framework to signal that
 * a parameterized query, aggregation, or database operation contains variable
 * references that have not been provided with actual values. This typically
 * occurs when executing predefined queries or aggregations that expect
 * parameter substitution but the required variables are missing.
 * </p>
 * <p>
 * Common scenarios where this exception is thrown include:
 * <ul>
 *   <li>Executing a parameterized aggregation pipeline without providing required variables</li>
 *   <li>Running a predefined query that expects path or query parameter substitution</li>
 *   <li>Processing template-based operations with missing variable bindings</li>
 *   <li>Attempting to resolve placeholder values that haven't been defined</li>
 *   <li>Using MongoDB aggregation variables (avars) that are not bound in the request</li>
 * </ul>
 * </p>
 * <p>
 * This is a checked exception that requires explicit handling by callers,
 * ensuring that unbound variables are properly caught and converted to
 * appropriate HTTP error responses for clients. The exception typically
 * results in a 400 Bad Request response when thrown during query processing.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class QueryVariableNotBoundException extends Exception {

    	/** Serial version UID for serialization compatibility. */
    	private static final long serialVersionUID = -8291349884609864832L;

    	/**
         * Constructs a new QueryVariableNotBoundException with no detail message.
         * <p>
         * This constructor creates an exception without a specific error message.
         * While functional, it's generally better to use the constructors that
         * accept a message parameter to provide more context about which variable
         * was not bound and in what context.
         * </p>
         */
    public QueryVariableNotBoundException() {
        super();
    }

    /**
     * Constructs a new QueryVariableNotBoundException with the specified detail message.
     * <p>
     * The detail message should clearly describe which variable was not bound
     * and in what context (e.g., aggregation pipeline, query template, etc.).
     * This message will typically be included in the HTTP error response sent
     * back to the client, so it should be informative but not expose internal
     * system details.
     * </p>
     *
     * @param message the detail message explaining which variable was not bound
     */
    public QueryVariableNotBoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new QueryVariableNotBoundException with the specified detail message and cause.
     * <p>
     * This constructor is useful when the variable binding fails due to an underlying
     * exception (such as template parsing errors, variable resolution failures, or
     * configuration loading problems). The cause provides additional context about
     * the root reason for the variable binding failure.
     * </p>
     *
     * @param message the detail message explaining which variable was not bound
     * @param cause the underlying cause of the variable binding failure
     */
    public QueryVariableNotBoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
