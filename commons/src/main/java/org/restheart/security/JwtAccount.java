/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Account implementation for JWT (JSON Web Token) based authentication.
 * 
 * <p>This class extends {@link BaseAccount} to provide account management for users authenticated
 * via JWT tokens. It stores the complete JWT payload as a JSON string, making all claims available
 * for authorization decisions and application logic.</p>
 * 
 * <h2>JWT Claims Handling</h2>
 * <p>The JWT payload is stored as a raw JSON string containing all claims from the token. This includes:</p>
 * <ul>
 *   <li><strong>Standard Claims:</strong> sub (subject), iss (issuer), exp (expiration), iat (issued at), etc.</li>
 *   <li><strong>Custom Claims:</strong> Any application-specific claims included in the JWT</li>
 *   <li><strong>Role Claims:</strong> Typically extracted and passed separately to the constructor</li>
 * </ul>
 * 
 * <h2>Property Access</h2>
 * <p>JWT claims can be accessed in two ways:</p>
 * <ol>
 *   <li><strong>As JSON String:</strong> Via {@link #properties()} for raw access or custom parsing</li>
 *   <li><strong>As Map:</strong> Via {@link #propertiesAsMap()} for convenient claim access</li>
 * </ol>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // JWT payload example
 * String jwtPayload = "{" +
 *     "\"sub\": \"user123\"," +
 *     "\"email\": \"user@example.com\"," +
 *     "\"roles\": [\"user\", \"admin\"]," +
 *     "\"exp\": 1234567890," +
 *     "\"custom_claim\": \"value\"" +
 * "}";
 * 
 * // Create JWT account
 * JwtAccount account = new JwtAccount(
 *     "user123",                    // username from 'sub' claim
 *     Set.of("user", "admin"),      // roles extracted from token
 *     jwtPayload                    // complete JWT payload
 * );
 * 
 * // Access claims as map
 * Map<String, Object> claims = account.propertiesAsMap();
 * String email = (String) claims.get("email");
 * Double exp = (Double) claims.get("exp");
 * }</pre>
 * 
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li>JWT tokens should be validated before creating JwtAccount instances</li>
 *   <li>Sensitive claims should be handled carefully and not logged</li>
 *   <li>Token expiration should be checked by the authentication mechanism</li>
 * </ul>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 5.0.0
 * @see BaseAccount
 * @see WithProperties
 */
public class JwtAccount extends BaseAccount implements WithProperties<String> {
    /**
     *
     */
    private static final long serialVersionUID = -2405615782892727187L;
    final private String properties;

    /**
     * Constructs a new JwtAccount with the specified name, roles, and JWT payload.
     * 
     * <p>Creates an account representing a user authenticated via JWT. The complete JWT payload
     * is stored as a JSON string, preserving all claims for later access.</p>
     * 
     * <p><strong>Note:</strong> This constructor does not validate the JWT token. Token validation
     * should be performed by the authentication mechanism before creating the account.</p>
     * 
     * @param name The username, typically extracted from the JWT 'sub' (subject) claim. Must not be null
     * @param roles The set of roles for this user, typically extracted from a custom JWT claim.
     *              Can be null or empty
     * @param properties The complete JWT payload as a JSON string containing all claims.
     *                   Should be a valid JSON object string
     * @throws IllegalArgumentException if name is null (inherited from BaseAccount)
     */
    public JwtAccount(final String name, final Set<String> roles, String properties) {
        super(name, roles);
        this.properties = properties;
    }

    @Override
    public String toString() {
        return super.toString().concat(" jwt=").concat(properties);
    }

    /**
     * Returns the JWT payload as a raw JSON string.
     * 
     * <p>This method provides direct access to the complete JWT payload, including all standard
     * and custom claims. The returned string is in JSON format and can be parsed to extract
     * individual claims.</p>
     * 
     * <p>Example return value:</p>
     * <pre>{@code
     * {
     *   "sub": "user123",
     *   "iss": "https://example.com",
     *   "exp": 1234567890,
     *   "email": "user@example.com",
     *   "roles": ["user", "admin"]
     * }
     * }</pre>
     * 
     * @return The JWT payload as a JSON string containing all token claims
     */
    @Override
    public String properties() {
        return properties;
    }

    private static Gson GSON = new GsonBuilder().serializeNulls().create();

    /**
     * Returns the JWT payload as a Map for convenient claim access.
     * 
     * <p>This method parses the JWT JSON payload and returns it as a Map, making it easy
     * to access individual claims. The map structure preserves the JSON types:</p>
     * <ul>
     *   <li>String claims remain as String objects</li>
     *   <li>Numeric claims are represented as Double objects</li>
     *   <li>Boolean claims remain as Boolean objects</li>
     *   <li>Array claims become List objects</li>
     *   <li>Nested object claims become nested Map objects</li>
     *   <li>Null values are preserved</li>
     * </ul>
     * 
     * <p><strong>Note:</strong> The returned map is created on each call, so modifications
     * to it will not affect the stored JWT payload.</p>
     * 
     * @return A Map representation of the JWT claims, where keys are claim names and values
     *         are the claim values with appropriate Java types
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, ? super Object> propertiesAsMap() {
        return GSON.fromJson(properties, HashMap.class);
    }
}
