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
 * Exception thrown when metadata associated with MongoDB resources is invalid or malformed.
 * <p>
 * This exception is used throughout the RESTHeart framework to signal that
 * metadata provided for databases, collections, or other MongoDB resources
 * does not conform to the expected format, contains invalid values, or
 * violates metadata validation rules.
 * </p>
 * <p>
 * Common scenarios where this exception is thrown include:
 * <ul>
 *   <li>Invalid JSON schema definitions for document validation</li>
 *   <li>Malformed aggregation pipeline definitions</li>
 *   <li>Invalid transformer configurations</li>
 *   <li>Incorrect checker or hook specifications</li>
 *   <li>Unsupported metadata properties or values</li>
 *   <li>Schema validation failures for metadata documents</li>
 * </ul>
 * </p>
 * <p>
 * This is a checked exception that requires explicit handling by callers,
 * ensuring that invalid metadata is properly caught and converted to
 * appropriate HTTP error responses for clients. The exception typically
 * results in a 400 Bad Request response when thrown during metadata
 * processing operations.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class InvalidMetadataException extends Exception {
	private static final long serialVersionUID = -4824392874427008468L;

	/**
     * Constructs a new InvalidMetadataException with no detail message.
     * <p>
     * This constructor creates an exception without a specific error message.
     * While functional, it's generally better to use the constructors that
     * accept a message parameter to provide more context about the validation
     * failure to help with debugging and client error responses.
     * </p>
     */
    public InvalidMetadataException() {
        super();
    }

    /**
     * Constructs a new InvalidMetadataException with the specified detail message.
     * <p>
     * The detail message should clearly describe what aspect of the metadata
     * is invalid and why it's considered invalid. This message will typically
     * be included in the HTTP error response sent back to the client, so it
     * should be informative but not expose internal system details.
     * </p>
     *
     * @param message the detail message explaining the invalid metadata
     */
    public InvalidMetadataException(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidMetadataException with the specified detail message and cause.
     * <p>
     * This constructor is useful when the metadata validation fails due to
     * an underlying exception (such as JSON parsing errors, schema validation
     * failures, or configuration loading problems). The cause provides additional
     * context about the root reason for the metadata validation failure.
     * </p>
     *
     * @param message the detail message explaining the invalid metadata
     * @param cause the underlying cause of the metadata validation failure
     */
    public InvalidMetadataException(String message, Throwable cause) {
        super(message, cause);
    }
}
