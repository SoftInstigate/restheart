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

package org.restheart.plugins;

import org.restheart.utils.HttpStatus;

/**
 * Exception thrown by interceptors to indicate processing errors.
 * 
 * <p>InterceptorException is a runtime exception that can be thrown by {@link Interceptor}
 * implementations to signal that request processing should be halted. When thrown, this
 * exception causes RESTHeart to stop the interceptor chain and return an error response
 * with the specified HTTP status code.</p>
 * 
 * <h2>Purpose</h2>
 * <p>This exception is designed to:</p>
 * <ul>
 *   <li>Provide a clean way to abort request processing from interceptors</li>
 *   <li>Allow interceptors to specify custom HTTP status codes</li>
 *   <li>Carry error messages to be included in error responses</li>
 *   <li>Maintain the cause chain for debugging purposes</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * public class ValidationInterceptor implements JsonInterceptor {
 *     @Override
 *     public void handle(JsonRequest request, JsonResponse response) {
 *         JsonElement content = request.getContent();
 *         
 *         if (content == null || !content.isJsonObject()) {
 *             throw new InterceptorException(
 *                 "Request body must be a JSON object",
 *                 HttpStatus.SC_BAD_REQUEST
 *             );
 *         }
 *         
 *         if (!isValid(content)) {
 *             throw new InterceptorException(
 *                 "Validation failed: " + getValidationErrors(content),
 *                 HttpStatus.SC_UNPROCESSABLE_ENTITY
 *             );
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h2>Status Codes</h2>
 * <p>Common HTTP status codes used with InterceptorException:</p>
 * <ul>
 *   <li>{@link HttpStatus#SC_BAD_REQUEST} (400) - Invalid request format</li>
 *   <li>{@link HttpStatus#SC_UNAUTHORIZED} (401) - Authentication required</li>
 *   <li>{@link HttpStatus#SC_FORBIDDEN} (403) - Access denied</li>
 *   <li>{@link HttpStatus#SC_NOT_FOUND} (404) - Resource not found</li>
 *   <li>{@link HttpStatus#SC_CONFLICT} (409) - Resource conflict</li>
 *   <li>{@link HttpStatus#SC_UNPROCESSABLE_ENTITY} (422) - Validation failed</li>
 *   <li>{@link HttpStatus#SC_INTERNAL_SERVER_ERROR} (500) - Server error (default)</li>
 * </ul>
 * 
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Always provide meaningful error messages</li>
 *   <li>Use appropriate HTTP status codes</li>
 *   <li>Include the original cause when wrapping other exceptions</li>
 *   <li>Avoid exposing sensitive information in error messages</li>
 * </ul>
 * 
 * @see Interceptor
 * @see HttpStatus
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class InterceptorException extends RuntimeException {
    /**
     *
     */
    private static final long serialVersionUID = -846615677223399751L;

    int statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;

    /**
     * Constructs a new InterceptorException with default status code 500.
     * 
     * <p>Creates an exception with no message and {@link HttpStatus#SC_INTERNAL_SERVER_ERROR}
     * as the status code.</p>
     */
    public InterceptorException() {
        super();
    }

    /**
     * Constructs a new InterceptorException with the specified status code.
     * 
     * <p>Creates an exception with no message but with a custom HTTP status code
     * that will be returned in the error response.</p>
     * 
     * @param statusCode the HTTP status code to return (e.g., 400, 403, 404)
     */
    public InterceptorException(int statusCode) {
        super();
        this.statusCode = statusCode;
    }

    /**
     * Constructs a new InterceptorException with the specified message.
     * 
     * <p>Creates an exception with an error message and default status code 500.
     * The message will be included in the error response.</p>
     * 
     * @param message the detail message explaining the error
     */
    public InterceptorException(String message) {
        super(message);
    }

    /**
     * Constructs a new InterceptorException with the specified message and status code.
     * 
     * <p>Creates an exception with both a custom error message and HTTP status code.
     * This is the most commonly used constructor.</p>
     * 
     * @param message the detail message explaining the error
     * @param statusCode the HTTP status code to return (e.g., 400, 403, 404)
     */
    public InterceptorException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Constructs a new InterceptorException with the specified message and cause.
     * 
     * <p>Creates an exception that wraps another throwable, preserving the stack trace
     * for debugging. Uses default status code 500.</p>
     * 
     * @param message the detail message explaining the error
     * @param cause the cause of this exception (can be retrieved later by {@link #getCause()})
     */
    public InterceptorException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new InterceptorException with message, status code, and cause.
     * 
     * <p>Creates a fully specified exception with custom message, HTTP status code,
     * and wrapped cause. Useful when catching and re-throwing exceptions with
     * additional context.</p>
     * 
     * @param message the detail message explaining the error
     * @param statusCode the HTTP status code to return (e.g., 400, 403, 404)
     * @param cause the cause of this exception (can be retrieved later by {@link #getCause()})
     */
    public InterceptorException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * Gets the HTTP status code associated with this exception.
     * 
     * <p>The status code will be used in the HTTP response when this exception
     * causes request processing to be aborted.</p>
     * 
     * @return the HTTP status code (defaults to 500 if not specified)
     */
    public int getStatusCode() {
        return statusCode;
    }
}
