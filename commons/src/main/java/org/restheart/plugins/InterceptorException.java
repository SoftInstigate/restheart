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

package org.restheart.plugins;

import org.restheart.utils.HttpStatus;

/**
 * Exception thrown by interceptors to signal errors during request/response processing.
 * <p>
 * InterceptorException is a specialized RuntimeException that allows interceptors to
 * communicate error conditions with specific HTTP status codes back to the RESTHeart
 * framework. When thrown by an interceptor, this exception terminates request processing
 * and generates an HTTP error response with the specified status code.
 * </p>
 * <p>
 * This exception is particularly useful for interceptors that need to:
 * <ul>
 *   <li><strong>Validate requests</strong> - Signal validation failures with appropriate 4xx status codes</li>
 *   <li><strong>Enforce security policies</strong> - Reject unauthorized requests with 401 or 403 status codes</li>
 *   <li><strong>Handle resource conflicts</strong> - Signal conflicts with 409 status codes</li>
 *   <li><strong>Report processing errors</strong> - Signal internal errors with 5xx status codes</li>
 *   <li><strong>Control request flow</strong> - Terminate processing at specific intercept points</li>
 * </ul>
 * </p>
 * <p>
 * Example usage in an interceptor:
 * <pre>
 * &#64;RegisterPlugin(name = "validationInterceptor", interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
 * public class ValidationInterceptor implements Interceptor&lt;JsonRequest, JsonResponse&gt; {
 *     &#64;Override
 *     public void handle(JsonRequest request, JsonResponse response) throws Exception {
 *         // Validate required header
 *         if (request.getHeader("X-API-Key") == null) {
 *             throw new InterceptorException("Missing required API key", HttpStatus.SC_BAD_REQUEST);
 *         }
 *         
 *         // Validate request content
 *         JsonObject content = request.getContent().asJsonObject();
 *         if (!content.containsKey("userId")) {
 *             throw new InterceptorException("Missing required field: userId", HttpStatus.SC_UNPROCESSABLE_ENTITY);
 *         }
 *         
 *         // Check user permissions
 *         if (!hasPermission(request.getAuthenticatedAccount(), content.getString("userId"))) {
 *             throw new InterceptorException("Insufficient permissions", HttpStatus.SC_FORBIDDEN);
 *         }
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Status Code Handling:</strong><br>
 * The exception includes an HTTP status code that will be used for the error response:
 * <ul>
 *   <li><strong>400 Bad Request</strong> - For malformed or invalid requests</li>
 *   <li><strong>401 Unauthorized</strong> - For authentication failures</li>
 *   <li><strong>403 Forbidden</strong> - For authorization failures</li>
 *   <li><strong>409 Conflict</strong> - For resource conflicts</li>
 *   <li><strong>422 Unprocessable Entity</strong> - For semantic validation failures</li>
 *   <li><strong>500 Internal Server Error</strong> - For unexpected processing errors (default)</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Framework Integration:</strong><br>
 * When an InterceptorException is thrown:
 * <ol>
 *   <li>Request processing is immediately terminated</li>
 *   <li>Subsequent interceptors in the pipeline are skipped</li>
 *   <li>An HTTP error response is generated with the specified status code</li>
 *   <li>The exception message is included in the error response body</li>
 *   <li>Appropriate error headers are set</li>
 * </ol>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Interceptor
 * @see InterceptPoint
 * @see org.restheart.utils.HttpStatus
 */
public class InterceptorException extends RuntimeException {
    /**
     *
     */
    private static final long serialVersionUID = -846615677223399751L;

    /** The HTTP status code to be returned when this exception is thrown */
    int statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;

    /**
     * Creates a new InterceptorException with a default status code of 500 Internal Server Error.
     * <p>
     * This constructor creates an exception without a specific error message, using the
     * default HTTP status code 500. This is typically used for unexpected internal errors
     * where no specific error message is available.
     * </p>
     */
    public InterceptorException() {
        super();
    }

    /**
     * Creates a new InterceptorException with the specified HTTP status code.
     * <p>
     * This constructor allows setting a specific HTTP status code without providing
     * an error message. The status code should correspond to the nature of the error
     * condition that caused the exception.
     * </p>
     *
     * @param statusCode the HTTP status code to be returned (e.g., 400, 401, 403, 404, 500)
     */
    public InterceptorException(int statusCode) {
        super();
        this.statusCode = statusCode;
    }

    /**
     * Creates a new InterceptorException with the specified error message.
     * <p>
     * This constructor creates an exception with a descriptive error message and
     * uses the default HTTP status code 500. The message will be included in the
     * error response sent to the client.
     * </p>
     *
     * @param message a descriptive error message explaining the cause of the exception
     */
    public InterceptorException(String message) {
        super(message);
    }

    /**
     * Creates a new InterceptorException with the specified error message and HTTP status code.
     * <p>
     * This is the most commonly used constructor, allowing both a descriptive error
     * message and a specific HTTP status code to be set. The message will be included
     * in the error response, and the status code will determine the HTTP response status.
     * </p>
     *
     * @param message a descriptive error message explaining the cause of the exception
     * @param statusCode the HTTP status code to be returned (e.g., 400, 401, 403, 404, 500)
     */
    public InterceptorException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Creates a new InterceptorException with the specified error message and underlying cause.
     * <p>
     * This constructor is useful when wrapping another exception that caused the interceptor
     * failure. The original exception is preserved as the cause, which aids in debugging
     * and error tracking. Uses the default HTTP status code 500.
     * </p>
     *
     * @param message a descriptive error message explaining the cause of the exception
     * @param cause the underlying exception that caused this interceptor exception
     */
    public InterceptorException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new InterceptorException with all parameters: message, status code, and cause.
     * <p>
     * This constructor provides complete control over the exception details, including
     * a descriptive error message, specific HTTP status code, and the underlying cause
     * exception. This is the most comprehensive constructor for complex error scenarios.
     * </p>
     *
     * @param message a descriptive error message explaining the cause of the exception
     * @param statusCode the HTTP status code to be returned (e.g., 400, 401, 403, 404, 500)
     * @param cause the underlying exception that caused this interceptor exception
     */
    public InterceptorException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * Returns the HTTP status code associated with this exception.
     * <p>
     * The status code indicates the type of HTTP error response that should be
     * generated when this exception is thrown by an interceptor. The RESTHeart
     * framework uses this status code to set the appropriate HTTP response status.
     * </p>
     * <p>
     * Common status codes include:
     * <ul>
     *   <li>400 - Bad Request (malformed or invalid request)</li>
     *   <li>401 - Unauthorized (authentication required or failed)</li>
     *   <li>403 - Forbidden (access denied)</li>
     *   <li>404 - Not Found (resource not found)</li>
     *   <li>409 - Conflict (resource conflict)</li>
     *   <li>422 - Unprocessable Entity (semantic validation failure)</li>
     *   <li>500 - Internal Server Error (unexpected error)</li>
     * </ul>
     * </p>
     *
     * @return the HTTP status code to be used in the error response
     */
    public int getStatusCode() {
        return statusCode;
    }
}
