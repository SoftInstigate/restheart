/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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
package org.restheart.security;

import java.util.Map;
/**
 * Utility interface for handling additional account properties in a type-safe manner.
 * 
 * <p>This interface provides a contract for account implementations that need to store
 * and expose additional properties beyond the basic authentication information (username,
 * roles). It supports different property storage formats while providing a common way
 * to access them.</p>
 * 
 * <h2>Purpose</h2>
 * <p>RESTHeart's flexible authentication system supports various account sources (files,
 * MongoDB, JWT tokens) that may contain rich metadata about users. This interface allows
 * account implementations to expose this metadata in both its native format and as a
 * standardized Map for interoperability.</p>
 * 
 * <h2>Type Parameter</h2>
 * <p>The type parameter {@code P} represents the native format of the properties:</p>
 * <ul>
 *   <li>{@link String} - For {@link JwtAccount}, storing raw JWT JSON payload</li>
 *   <li>{@link org.bson.BsonDocument} - For {@link MongoRealmAccount}, storing MongoDB documents</li>
 *   <li>{@link Map} - For {@link FileRealmAccount}, storing configuration maps</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Generic handling of account properties
 * if (account instanceof WithProperties<?>) {
 *     WithProperties<?> withProps = (WithProperties<?>) account;
 *     
 *     // Access as map for generic processing
 *     Map<String, Object> props = withProps.propertiesAsMap();
 *     String email = (String) props.get("email");
 *     
 *     // Access native format for specific handling
 *     if (withProps instanceof WithProperties<BsonDocument>) {
 *         BsonDocument bsonProps = ((WithProperties<BsonDocument>) withProps).properties();
 *         // Use BSON-specific features
 *     }
 * }
 * }</pre>
 * 
 * <h2>Implementation Guidelines</h2>
 * <p>When implementing this interface:</p>
 * <ol>
 *   <li>The {@link #properties()} method should return properties in their native format</li>
 *   <li>The {@link #propertiesAsMap()} method should convert properties to a Map representation</li>
 *   <li>Null properties should be handled gracefully (return null or empty map)</li>
 *   <li>The Map representation should preserve as much type information as possible</li>
 *   <li>Both methods should return consistent data (same properties, different format)</li>
 * </ol>
 * 
 * @param <P> The type of the native property storage format
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 5.0.0
 * @see FileRealmAccount
 * @see MongoRealmAccount
 * @see JwtAccount
 */
public interface WithProperties<P> {
    /**
     * Returns the account properties in their native storage format.
     * 
     * <p>This method provides access to properties in their original format, preserving
     * all type information and structure specific to the account source. The actual type
     * returned depends on the implementation:</p>
     * <ul>
     *   <li>JWT accounts return the raw JSON string</li>
     *   <li>MongoDB accounts return a BsonDocument</li>
     *   <li>File accounts return a Map</li>
     * </ul>
     * 
     * <p>Using the native format allows for source-specific operations and optimizations,
     * such as BSON queries for MongoDB accounts or JSON path expressions for JWT accounts.</p>
     * 
     * @return The properties in their native format, or null if no properties exist
     */
    P properties();

    /**
     * Returns the account properties as a standard Java Map.
     * 
     * <p>This method provides a uniform way to access account properties regardless of
     * their native storage format. The conversion should preserve data types as much
     * as possible while ensuring compatibility with standard Java collections.</p>
     * 
     * <h3>Type Mapping Guidelines</h3>
     * <ul>
     *   <li>Strings, numbers, and booleans should maintain their types</li>
     *   <li>Nested objects should become nested Maps</li>
     *   <li>Arrays should become Lists</li>
     *   <li>Null values should be preserved</li>
     *   <li>Special types (dates, binary data) should use appropriate Java representations</li>
     * </ul>
     * 
     * <p>This method is particularly useful for:</p>
     * <ul>
     *   <li>Generic property access in authorization rules</li>
     *   <li>Serialization to JSON for REST APIs</li>
     *   <li>Integration with expression languages</li>
     *   <li>Logging and debugging</li>
     * </ul>
     * 
     * @return A Map representation of the properties with String keys and Object values,
     *         or null if no properties exist
     */
    Map<String, ? super Object> propertiesAsMap();
}
