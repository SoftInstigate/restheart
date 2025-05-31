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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bson.BsonDocument;
import org.restheart.utils.BsonUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Account implementation for MongoDB-based authentication realms.
 *
 * <p>This class extends {@link PwdCredentialAccount} to provide account management for users
 * authenticated via MongoDB-stored credentials. It stores user data retrieved from MongoDB
 * collections, including custom properties that can be used for authorization and application logic.</p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><strong>MongoDB Integration:</strong> Designed to work with MongoRealmAuthenticator</li>
 *   <li><strong>Database Context:</strong> Stores the database name for multi-tenant scenarios</li>
 *   <li><strong>BSON Properties:</strong> Native support for MongoDB BSON documents as properties</li>
 *   <li><strong>Password Security:</strong> Inherits secure password handling from PwdCredentialAccount</li>
 * </ul>
 *
 * <h2>Property Storage</h2>
 * <p>Properties are stored as a BsonDocument, preserving the rich data types available in MongoDB:</p>
 * <ul>
 *   <li>Nested documents for complex user profiles</li>
 *   <li>Arrays for multi-valued attributes (roles, permissions, etc.)</li>
 *   <li>Dates, ObjectIds, and other BSON types</li>
 *   <li>Direct compatibility with MongoDB queries and updates</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create account from MongoDB document
 * BsonDocument userDoc = BsonDocument.parse(
 *     "{ 'email': 'user@example.com', " +
 *     "  'profile': { 'firstName': 'John', 'lastName': 'Doe' }, " +
 *     "  'permissions': ['read', 'write'], " +
 *     "  'lastLogin': { '$date': '2024-01-01T00:00:00Z' } }"
 * );
 *
 * MongoRealmAccount account = new MongoRealmAccount(
 *     "mydb",                          // database name
 *     "john.doe",                      // username
 *     "password123".toCharArray(),     // password
 *     Set.of("user", "developer"),     // roles
 *     userDoc                          // properties from MongoDB
 * );
 *
 * // Access properties
 * String email = account.properties().getString("email").getValue();
 * BsonDocument profile = account.properties().getDocument("profile");
 * }</pre>
 *
 * <h2>Multi-Tenancy Support</h2>
 * <p>The database field enables multi-tenant applications where users are stored in different
 * MongoDB databases. This allows for complete data isolation between tenants while using the
 * same authentication mechanism.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 5.0.0
 * @see PwdCredentialAccount
 * @see WithProperties
 * @see org.restheart.plugins.security.authenticators.MongoRealmAuthenticator
 */
public class MongoRealmAccount extends PwdCredentialAccount implements WithProperties<BsonDocument> {
    private static final long serialVersionUID = -5840534832968478775L;

    private final BsonDocument properties;

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    /**
     * Constructs a new MongoRealmAccount with the specified credentials, database, roles, and properties.
     *
     * <p>Creates an account representing a user authenticated against a MongoDB realm. The account
     * includes the database context, making it suitable for multi-tenant applications where users
     * may exist in different databases.</p>
     *
     * <p><strong>Security Note:</strong> The password char array should be cleared after use
     * to minimize the time sensitive data remains in memory. The properties BsonDocument may
     * contain sensitive information and should be handled accordingly.</p>
     *
     * @param db The MongoDB database name where this user account is stored. Used for multi-tenant
     *           scenarios and database-scoped operations
     * @param name The username for this account. Must not be null
     * @param password The password as a char array. Must not be null. Should be cleared after use
     * @param roles The set of roles assigned to this account. Can be null or empty
     * @param properties Additional properties stored with the user in MongoDB. Can be null, though
     *                   typically contains user metadata, preferences, and application-specific data
     * @throws IllegalArgumentException if name or password is null (inherited from PwdCredentialAccount)
     */
    public MongoRealmAccount(final String name, final char[] password, final Set<String> roles, BsonDocument properties) {
        super(name, password, roles);

        if (password == null) {
            throw new IllegalArgumentException("argument password cannot be null");
        }

        this.properties = properties;
    }

    /**
     * Returns the BSON document containing this account's properties.
     *
     * <p>The returned document contains all custom properties stored with the user in MongoDB.
     * This preserves the full richness of BSON data types, including:</p>
     * <ul>
     *   <li>Nested documents for structured data</li>
     *   <li>Arrays for multi-valued properties</li>
     *   <li>Dates, ObjectIds, Binary data, and other BSON types</li>
     * </ul>
     *
     * <p><strong>Note:</strong> The returned document is the internal properties object.
     * While BSON documents are generally immutable, care should be taken not to modify
     * the returned document if it needs to remain unchanged.</p>
     *
     * @return The BSON document containing user properties, may be null if no properties exist
     */
    @Override
    public BsonDocument properties() {
        return properties;
    }


    /**
     * Converts the BSON properties to a Java Map for convenient access.
     *
     * <p>This method transforms the BSON document properties into a standard Java Map while
     * preserving the BSON strict JSON representation. This is particularly useful when:</p>
     * <ul>
     *   <li>Interfacing with non-BSON aware components</li>
     *   <li>Serializing to JSON for REST APIs</li>
     *   <li>Using properties in expression languages or templates</li>
     * </ul>
     *
     * <h3>Type Preservation</h3>
     * <p>The conversion uses GSON with BSON's strict mode to maintain type fidelity:</p>
     * <ul>
     *   <li>Dates: Represented as {@code {"$date": 1234567890000}}</li>
     *   <li>ObjectIds: Represented as {@code {"$oid": "507f1f77bcf86cd799439011"}}</li>
     *   <li>Binary: Represented as {@code {"$binary": {"base64": "...", "subType": "00"}}}</li>
     * </ul>
     *
     * <p>This approach preserves BSON type information, unlike {@code BsonUtils.bsonToDocument()}
     * which would convert dates to longs and lose type metadata.</p>
     *
     * @return A Map representation of the properties with BSON type preservation, or null if
     *         no properties exist
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, ? super Object> propertiesAsMap() {
        if (properties == null) {
            return null;
        }

        // we use GSON rather than BsonUtils.bsonToDocument()
        // to preserve the BSON strict representation format
        // as in d: { "$date": 123.0 }; using Document it will turn it to d: 0
        return GSON.fromJson(BsonUtils.toJson(properties), HashMap.class);
    }
}
