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
 * Exception thrown when a document ID type is not supported by the current operation or context.
 * <p>
 * This exception is used throughout the RESTHeart framework to signal that a document ID
 * provided in a request uses a data type that is not supported or allowed for the specific
 * operation being performed. MongoDB supports various data types for document IDs, but
 * certain operations or configurations may restrict which types are permitted.
 * </p>
 * <p>
 * Common scenarios where this exception is thrown include:
 * <ul>
 *   <li>Using array types as document IDs (not supported by MongoDB)</li>
 *   <li>Attempting to use complex object types as IDs in restricted contexts</li>
 *   <li>Using ID types that conflict with collection-specific validation rules</li>
 *   <li>Providing IDs in formats not supported by specific service implementations</li>
 *   <li>Using deprecated or unsafe ID types in security-sensitive operations</li>
 * </ul>
 * </p>
 * <p>
 * This is a checked exception that requires explicit handling by callers,
 * ensuring that unsupported ID types are properly caught and converted to
 * appropriate HTTP error responses for clients. The exception typically
 * results in a 400 Bad Request response when thrown during document operations.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class UnsupportedDocumentIdException extends Exception {

    /** Serial version UID for serialization compatibility. */
    private static final long serialVersionUID = -5994487927482596996L;

    /**
     * Constructs a new UnsupportedDocumentIdException with no detail message.
     * <p>
     * This constructor creates an exception without a specific error message.
     * While functional, it's generally better to use the constructors that
     * accept a message parameter to provide more context about which document
     * ID type is unsupported and why.
     * </p>
     */
    public UnsupportedDocumentIdException() {
        super();
    }

    /**
     * Constructs a new UnsupportedDocumentIdException with the specified detail message.
     * <p>
     * The detail message should clearly describe which document ID type is
     * unsupported and provide context about why it's not allowed in the current
     * operation. This message will typically be included in the HTTP error
     * response sent back to the client.
     * </p>
     *
     * @param message the detail message explaining which document ID type is unsupported
     */
    public UnsupportedDocumentIdException(String message) {
        super(message);
    }

    /**
     * Constructs a new UnsupportedDocumentIdException with the specified detail message and cause.
     * <p>
     * This constructor is useful when the document ID validation fails due to
     * an underlying exception (such as type conversion errors, validation failures,
     * or configuration parsing problems). The cause provides additional context
     * about the root reason for the ID type validation failure.
     * </p>
     *
     * @param message the detail message explaining which document ID type is unsupported
     * @param cause the underlying cause of the ID type validation failure
     */
    public UnsupportedDocumentIdException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new UnsupportedDocumentIdException with the specified cause.
     * <p>
     * This constructor is useful when the document ID validation fails due to
     * an underlying exception and the cause itself provides sufficient context
     * about the nature of the problem. The detail message will be derived from
     * the cause's message.
     * </p>
     *
     * @param cause the underlying cause of the ID type validation failure
     */
    public UnsupportedDocumentIdException(Throwable cause) {
        super(cause);
    }
}
