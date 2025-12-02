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

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bson.*;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.*;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jayway.jsonpath.JsonPath;

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateParser;

/**
 * Helper class that provides variable interpolation capabilities for Access Control List (ACL) permissions.
 *
 * <p>This class enables dynamic substitution of runtime variables within ACL configurations, allowing
 * for context-aware authorization rules. It supports interpolation in various formats including BSON
 * documents, JSON strings, and Undertow predicates.</p>
 *
 * <h2>Supported Variables</h2>
 * <p>
 * The interpolator recognizes the following variable patterns:
 * </p>
 * <ul>
 * <li><strong>@user</strong> - The authenticated user's principal name</li>
 * <li><strong>@user.roles</strong> - Array of user's roles</li>
 * <li><strong>@user.&lt;property&gt;</strong> - Any custom property from the user's account</li>
 * <li><strong>@request</strong> - The current request object</li>
 * <li><strong>@request.&lt;path&gt;</strong> - Specific request properties using JSONPath syntax</li>
 * <li><strong>@request.body</strong> - The request body content (for BsonRequest/JsonRequest)</li>
 * <li><strong>@request.body.&lt;path&gt;</strong> - Specific request body properties using dot notation</li>
 * <li><strong>@now</strong> - Current timestamp as epoch milliseconds</li>
 * <li><strong>@mongoPermissions</strong> - MongoDB-specific permissions object</li>
 * </ul>
 *
 * <h2>Legacy Support</h2>
 * <p>
 * For backward compatibility, the following legacy variables are also supported:
 * </p>
 * <ul>
 * <li><strong>%USER</strong> - Equivalent to @user</li>
 * <li><strong>%ROLES</strong> - Equivalent to @user.roles</li>
 * <li><strong>%NOW</strong> - Equivalent to @now</li>
 * </ul>
 *
 * <h2>Authentication Support</h2>
 * <p>
 * The interpolator supports various authentication mechanisms:
 * </p>
 * <ul>
 * <li>{@link MongoRealmAccount} - MongoDB-based authentication</li>
 * <li>{@link FileRealmAccount} - File-based authentication</li>
 * <li>{@link JwtAccount} - JWT token authentication</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // BSON document with variables
 * BsonDocument permission = BsonDocument.parse(
 *     "{ 'owner': '@user', 'timestamp': { '$lte': '@now' } }"
 * );
 *
 * // Interpolate variables
 * BsonDocument interpolated = AclVarsInterpolator.interpolateBson(
 *         request, permission);
 *
 * // Using @request.body in ACL predicates for authorization
 * // Example: restrict transaction amount
 * // predicate: "method(POST) and lte(@request.body.amount, 10000)"
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 5.0.0
 */
public class AclVarsInterpolator {
    private static final Logger LOGGER = LoggerFactory.getLogger(AclVarsInterpolator.class);

    /**
     * Interpolates variable references in a BSON value with their runtime values.
     *
     * <p>
     * This method recursively processes BSON documents and arrays, replacing variable
     * references with their actual values from the request context. String values containing
     * variables are parsed and substituted, while other BSON types are returned unchanged.
     * </p>
     *
     * <p>
     * Supported variable patterns:
     * </p>
     * <ul>
     * <li><strong>@user</strong> - Authenticated user's principal name</li>
     * <li><strong>@user.roles</strong> - User's roles as a BSON array</li>
     * <li><strong>@user.property</strong> - Custom user properties</li>
     * <li><strong>@now</strong> - Current timestamp in milliseconds</li>
     * <li><strong>@request.path</strong> - Request properties via JSONPath</li>
     * <li><strong>@mongoPermissions</strong> - MongoDB permissions object</li>
     * </ul>
     *
     * <p>
     * Legacy variables (%USER, %ROLES, %NOW) are also supported for backward compatibility.
     * </p>
     *
     * @param request
     *            The current request containing authentication and context information
     * @param bson
     *            The BSON value to interpolate. Can be a document, array, string, or any BSON type
     * @return A new BSON value with all variable references replaced by their actual values.
     *         Returns the original value if no interpolation is needed
     * @throws IllegalArgumentException if request is null
     * @see #
     */
    public static BsonValue interpolateBson(final MongoRequest request, final BsonValue bson) {
        if (bson.isDocument()) {
            var ret = new BsonDocument();
            var doc = bson.asDocument();
            doc.keySet().stream().forEach(k -> {
                var value = doc.get(k);

                if (value.isString()) {
                    ret.put(k, interpolatePropValue(request, k, doc.get(k).asString().getValue()));
                } else if (value.isDocument()) {
                    ret.put(k, interpolateBson(request, value));
                } else if (value.isArray()) {
                    var array = new BsonArray();
                    value.asArray().stream().forEachOrdered(e -> array.add(interpolateBson(request, e)));
                    ret.put(k, array);
                } else {
                    ret.put(k, value);
                }
            });
            return ret;
        } else if (bson.isArray()) {
            var ret = new BsonArray();
            bson.asArray().stream().forEachOrdered(ae -> ret.add(interpolateBson(request, ae)));
            return ret;
        } else if (bson.isString()) {
            return interpolatePropValue(request, null, bson.asString().getValue());
        } else {
            return bson;
        }
    }

    /**
     * Interpolates a string value containing variable references with their runtime values.
     *
     * <p>
     * This method processes string values that contain special variable patterns and replaces
     * them with corresponding runtime values from the request context. It supports both modern
     * (@-prefixed) and legacy (%-prefixed) variable syntax.
     * </p>
     *
     * <h3>Supported Variables</h3>
     * <table border="1">
     * <tr>
     * <th>Variable</th>
     * <th>Description</th>
     * <th>Return Type</th>
     * </tr>
     * <tr>
     * <td>@user</td>
     * <td>Complete user account object</td>
     * <td>BsonDocument</td>
     * </tr>
     * <tr>
     * <td>@user.property</td>
     * <td>Specific user property via dot notation</td>
     * <td>BsonValue</td>
     * </tr>
     * <tr>
     * <td>@request</td>
     * <td>Complete request object</td>
     * <td>BsonDocument</td>
     * </tr>
     * <tr>
     * <td>@request.property</td>
     * <td>Request property via JSONPath</td>
     * <td>BsonValue</td>
     * </tr>
     * <tr>
     * <td>@request.body</td>
     * <td>Complete request body content</td>
     * <td>BsonValue</td>
     * </tr>
     * <tr>
     * <td>@request.body.property</td>
     * <td>Request body property via dot notation</td>
     * <td>BsonValue</td>
     * </tr>
     * <tr>
     * <td>@mongoPermissions</td>
     * <td>MongoDB permissions object</td>
     * <td>BsonDocument</td>
     * </tr>
     * <tr>
     * <td>@mongoPermissions.property</td>
     * <td>Specific permission property</td>
     * <td>BsonValue</td>
     * </tr>
     * <tr>
     * <td>@filter</td>
     * <td>Current filter document</td>
     * <td>BsonDocument</td>
     * </tr>
     * <tr>
     * <td>@now</td>
     * <td>Current timestamp</td>
     * <td>BsonDateTime</td>
     * </tr>
     * <tr>
     * <td>%USER</td>
     * <td>Username (legacy)</td>
     * <td>BsonString</td>
     * </tr>
     * <tr>
     * <td>%ROLES</td>
     * <td>User roles (legacy)</td>
     * <td>BsonArray</td>
     * </tr>
     * <tr>
     * <td>%NOW</td>
     * <td>Current timestamp (legacy)</td>
     * <td>BsonDateTime</td>
     * </tr>
     * </table>
     *
     * <h3>User Account Support</h3>
     * <p>
     * The @user variable adapts to different authentication mechanisms:
     * </p>
     * <ul>
     * <li><strong>MongoRealmAccount:</strong> Returns the account's properties document directly</li>
     * <li><strong>FileRealmAccount:</strong> Converts properties map to BSON document</li>
     * <li><strong>JwtAccount:</strong> Returns JWT claims excluding standard fields (exp, iss, sub)</li>
     * </ul>
     *
     * <h3>Property Access</h3>
     * <p>
     * Nested properties can be accessed using dot notation:
     * </p>
     * <ul>
     * <li>Simple properties: @user.email, @request.method, @request.body.amount</li>
     * <li>Nested properties: @user.profile.firstName, @request.body.transaction.amount</li>
     * <li>JSONPath expressions: @request.headers.contentType</li>
     * </ul>
     * <p>
     * Note: @request.body variables require the request to be a BsonRequest (MongoRequest, etc.)
     * with BsonDocument content. They are evaluated during request processing and can be used
     * in authorization predicates to restrict operations based on request payload values.
     * </p>
     *
     * @param request
     *            The current request containing authentication and context information
     * @param key
     *            The property key (used for error reporting, can be null)
     * @param value
     *            The string value potentially containing variables to interpolate
     * @return The interpolated BSON value. Returns BsonNull.VALUE if the variable cannot be resolved
     *         or if the value parameter is null. For non-variable strings, returns a BsonString
     * @see #interpolateBson(MongoRequest, BsonValue)
     */
    public static BsonValue interpolatePropValue(MongoRequest request, String key, String value) {
        if (value == null) {
            return BsonNull.VALUE;
        } else if ("%USER".equals(value)) {
            return new BsonString(ExchangeAttributes.remoteUser().readAttribute(request.getExchange()));
        } else if ("%ROLES".equals(value)) {
            if (Objects.nonNull(request.getAuthenticatedAccount())
                    && Objects.nonNull(request.getAuthenticatedAccount().getRoles())) {
                var ret = new BsonArray();
                request.getAuthenticatedAccount().getRoles().stream().map(s -> new BsonString(s))
                        .forEachOrdered(ret::add);
                return ret;
            } else {
                return new BsonArray();
            }
        } else if ("%NOW".equals(value) || "@now".equals(value)) {
            return new BsonDateTime(Instant.now().getEpochSecond() * 1000);
        } else if (value.equals("@user")) {
            if (request == null || request.getAuthenticatedAccount() == null) {
                return BsonNull.VALUE;
            }

            return switch (request.getAuthenticatedAccount()) {
                case MongoRealmAccount maccount -> maccount.properties();
                case FileRealmAccount faccount -> toBson(faccount.properties());
                case JwtAccount jwtAccount -> {
                    var jwt = jwtAccount.propertiesAsMap();
                    // remove jwt specific fields, only using user properties
                    jwt.remove("exp");
                    jwt.remove("iss");
                    jwt.remove("sub");
                    yield toBson(jwt);
                }
                default -> BsonNull.VALUE;
            };
        } else if (value.equals("@filter")) {
            return request.getFiltersDocument();
        } else if (value.equals("@request")) {
            return getRequestObject(request);
        } else if (value.startsWith("@request.") && value.length() > 8) {
            var requestObject = getRequestObject(request);
            var prop = value.substring(9);

            LOGGER.debug("request doc: {}", requestObject.toJson());

            if (prop.contains(".")) {
                try {
                    var v = JsonPath.read(requestObject.toJson(), "$.".concat(prop));

                    return BsonUtils.parse(v.toString());
                } catch (Throwable pnfe) {
                    return BsonNull.VALUE;
                }
            } else {
                if (requestObject.containsKey(prop)) {
                    return requestObject.get(prop);
                } else {
                    return BsonNull.VALUE;
                }
            }
        } else if (value.equals("@request.body")) {
            final BsonValue content;
            try {
              content = request.getContent();
            } catch (Throwable t) {
              LOGGER.debug("Error getting request content", t);
              return BsonNull.VALUE;
            }

            if (content == null) {
                return BsonNull.VALUE;
            }
            return content;
        } else if (value.startsWith("@request.body.") && value.length() > 13) {
            final BsonValue content;
            try {
              content = request.getContent();
            } catch (Throwable t) {
              LOGGER.debug("Error getting request content", t);
              return BsonNull.VALUE;
            }

            if (content == null) {
                return BsonNull.VALUE;
            }

            if (!(content instanceof BsonDocument contentDoc)) {
                LOGGER.warn("@request.body variable used but request content is not a BsonDocument, it is {}", content.getClass().getSimpleName());
                return BsonNull.VALUE;
            }

            var prop = value.substring(14);

            LOGGER.debug("request body doc: {}", contentDoc.toJson());

            if (prop.contains(".")) {
                // Use BsonUtils.get() for dot notation support
                var _value = BsonUtils.get(contentDoc, prop);
                return _value.orElse(BsonNull.VALUE);
            } else {
                if (contentDoc.containsKey(prop)) {
                    return contentDoc.get(prop);
                } else {
                    return BsonNull.VALUE;
                }
            }
        } else if (value.equals("@mongoPermissions")) {
            if (MongoPermissions.of(request) != null) {
                return MongoPermissions.of(request).asBson();
            } else {
                return BsonNull.VALUE;
            }
        } else if (value.startsWith("@mongoPermissions.") && value.length() > 17) {
            if (MongoPermissions.of(request) != null) {
                var doc = MongoPermissions.of(request).asBson();
                var prop = value.substring(18);

                LOGGER.debug("permission doc: {}", doc);

                if (prop.contains(".")) {
                    try {
                        JsonElement v = JsonPath.read(doc.toJson(), "$.".concat(prop));

                        return BsonUtils.parse(v.toString());
                    } catch (Throwable pnfe) {
                        return BsonNull.VALUE;
                    }
                } else {
                    if (doc.containsKey(prop)) {
                        return doc.get(prop);
                    } else {
                        return BsonNull.VALUE;
                    }
                }
            } else {
                return BsonNull.VALUE;
            }
        } else if (value.startsWith("@user.") && value.length() > 5) {
            if (request.getAuthenticatedAccount() instanceof WithProperties<?> accountWithProperties) {
                return fromProperties(accountWithProperties.propertiesAsMap(), value.substring(6));
            } else {
                return BsonNull.VALUE;
            }
        } else {
            return new BsonString(value);
        }
    }

    /**
     * interpolate the permission predicate substituting @user.x variables
     *
     * @param predicate
     *            the predicate containing the placeholder valiable to
     *            interpolate
     * @param request
     *            the request
     * @param classLoader
     *            the classloader to resolve the predicates, see java.util.ServiceLoader
     * @return the interpolated predicate
     */
    public static Predicate interpolatePredicate(Request<?> request, String predicate, ClassLoader classLoader) throws ConfigurationException {
        var a = getAccountDocument(request);

        try {
            // Normalize quotes in the predicate by replacing them with placeholder
            var interpolatedPredicate = normalizeQuotes(predicate);

            // Interpolate @user variables
            if (a != null && !a.isEmpty()) {
                interpolatedPredicate = interpolatePredicate(interpolatedPredicate, "@user.", a);
            }

            // Interpolate @request.body variables
			BsonDocument requestBody = null;
			try {
				requestBody = getRequestBodyDocument(request);
			} catch(BadRequestException bre) {
				// nothing to do
			}

            if (requestBody != null && !requestBody.isEmpty()) {
                interpolatedPredicate = interpolatePredicate(interpolatedPredicate, "@request.body.", requestBody);
            }

            return PredicateParser.parse(interpolatedPredicate, classLoader);
        } catch (Throwable t) {
            throw new ConfigurationException("Wrong permission: invalid predicate " + predicate, t);
        }
    }

    private static BsonDocument getAccountDocument(Request<?> request) {
        if (request == null || request.getAuthenticatedAccount() == null) {
            return null;
        }

        return switch (request.getAuthenticatedAccount()) {
            case MongoRealmAccount maccount -> maccount.properties();
            case FileRealmAccount faccount -> toBson(faccount.properties()).asDocument();
            case JwtAccount jwtAccount -> toBson(jwtAccount.propertiesAsMap()).asDocument();
            default -> null;
        };
    }

    /**
     * Extracts the request body as a BSON document.
     * <p>
     * This helper method provides a unified way to access request body content regardless
     * of whether the request is a BsonRequest or JsonRequest. It handles the conversion
     * of different request types to a consistent BSON document format.
     * </p>
     *
     * @param request The request to extract the body from
     * @return A BsonDocument containing the request body, or null if no body is available or not supported
     */
    private static BsonDocument getRequestBodyDocument(Request<?> request) throws BadRequestException {
  		return switch (request) {
  			case null -> null;
  			case BsonRequest bsonRequest -> {
  			  final BsonValue content;
          try {
            content = bsonRequest.getContent();
          } catch (Throwable t) {
            LOGGER.debug("Error getting request content", t);
            yield null;
          }
  				if (content == null) {
  					yield null;
  				}
  				if (content instanceof BsonDocument doc) {
  					yield doc;
  				} else {
  					LOGGER.debug("BsonRequest content is not a BsonDocument, it is {}", content.getClass().getSimpleName());
  					yield null;
  				}
  			}
  			case JsonRequest jsonRequest -> {
          final JsonElement content;
          try {
            content = jsonRequest.getContent();
          } catch (Throwable t) {
            LOGGER.debug("Error getting request content", t);
            yield null;
          }
  				if (content == null) {
  					yield null;
  				}
  				if (content.isJsonObject()) {
  					yield jsonObjectToBsonDocument(content.getAsJsonObject());
  				} else {
  					LOGGER.debug("JsonRequest content is not a JsonObject, it is {}", content.getClass().getSimpleName());
  					yield null;
  				}
  			}
  			default -> null;
  		};
    }

    /**
     * Converts a Gson JsonObject to a BSON document.
     *
     * @param jsonObject The JsonObject to convert
     * @return A BsonDocument representation of the JsonObject
     */
    private static BsonDocument jsonObjectToBsonDocument(JsonObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }

        var doc = new BsonDocument();

        jsonObject.entrySet().forEach(entry -> {
            var key = entry.getKey();
            var value = entry.getValue();
            doc.put(key, jsonElementToBsonValue(value));
        });

        return doc;
    }

    /**
     * Converts a Gson JsonElement to a BsonValue.
     *
     * @param element The JsonElement to convert
     * @return A BsonValue representation of the JsonElement
     */
    private static BsonValue jsonElementToBsonValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return BsonNull.VALUE;
        } else if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                return new BsonString(primitive.getAsString());
            } else if (primitive.isNumber()) {
                // Try to preserve integer types
                try {
                    return new BsonInt32(primitive.getAsInt());
                } catch (NumberFormatException e) {
                    try {
                        return new BsonInt64(primitive.getAsLong());
                    } catch (NumberFormatException e2) {
                        return new BsonDouble(primitive.getAsDouble());
                    }
                }
            } else if (primitive.isBoolean()) {
                return new BsonBoolean(primitive.getAsBoolean());
            }
        } else if (element.isJsonObject()) {
            return jsonObjectToBsonDocument(element.getAsJsonObject());
        } else if (element.isJsonArray()) {
            var array = new BsonArray();
            element.getAsJsonArray().forEach(e -> array.add(jsonElementToBsonValue(e)));
            return array;
        }

        return BsonNull.VALUE;
    }

    /**
     * Extracts the account properties as a BSON document from the authenticated account.
     *
     * <p>
     * This helper method provides a unified way to access account properties regardless
     * of the authentication mechanism used. It handles the conversion of different account
     * types to a consistent BSON document format.
     * </p>
     *
     * @param request
     *            The request containing the authenticated account
     * @return A BsonDocument containing the account properties, or null if no account is authenticated
     */
    /**
     * Constructs a comprehensive BSON document representing the current HTTP request.
     *
     * <p>
     * This method creates a detailed representation of the request including all
     * relevant properties that might be needed for authorization decisions. The resulting
     * document can be queried using JSONPath expressions in ACL configurations.
     * </p>
     *
     * <h3>Request Document Structure</h3>
     *
     * <pre>{@code
     * {
     *   "path": "/api/users",
     *   "method": "GET",
     *   "remoteIp": "192.168.1.100",
     *   "secured": true,
     *   "scheme": "https",
     *   "host": "api.example.com",
     *   "port": 443,
     *   "queryParameters": { "page": ["1"], "size": ["10"] },
     *   "headers": { "Authorization": ["Bearer ..."], "Content-Type": ["application/json"] },
     *   "mappedPath": "/api/{collection}",
     *   "pathParams": { "collection": "users" },
     *   "content": { ... },          // For MongoRequest
     *   "aggregationVars": { ... }   // For MongoRequest with aggregation
     * }
     * }</pre>
     *
     * @param request
     *            The HTTP request to convert to BSON
     * @return A BsonDocument containing all request properties
     */
    private static BsonDocument getRequestObject(Request<?> request) {
        if (request == null || request.getAuthenticatedAccount() == null) {
            return null;
        }

        return switch (request.getAuthenticatedAccount()) {
            case MongoRealmAccount maccount -> maccount.properties();
            case FileRealmAccount faccount -> toBson(faccount.properties()).asDocument();
            case JwtAccount jwtAccount -> toBson(jwtAccount.propertiesAsMap()).asDocument();
            default -> null;
        };
    }

    /**
     * Interpolates variables in a predicate string by substituting them with values from a BSON document.
     *
     * <p>
     * This method performs variable substitution in Undertow predicate expressions by replacing
     * variable references with their actual values. It handles different data types appropriately:
     * </p>
     * <ul>
     * <li><strong>Primitive values:</strong> Strings, numbers, booleans are properly quoted</li>
     * <li><strong>Arrays:</strong> Converted to comma-separated lists suitable for predicates</li>
     * <li><strong>Nested properties:</strong> Accessed via flattened dot notation</li>
     * <li><strong>Null/missing values:</strong> Variables are removed from the predicate</li>
     * </ul>
     *
     * <h3>Variable Resolution Process</h3>
     * <ol>
     * <li>The variableValues document is flattened to handle nested properties</li>
     * <li>Primitive values are interpolated with appropriate quoting</li>
     * <li>Arrays are converted to predicate-compatible format</li>
     * <li>Unbound variables (not found in variableValues) are removed</li>
     * </ol>
     *
     * <h3>Example</h3>
     *
     * <pre>{@code
     * // Given predicate: "roles[@user.roles] and equals[@user.department, 'IT']"
     * // And variableValues: { "roles": ["admin", "user"], "department": "IT" }
     * // Result: "roles[admin,user] and equals['IT', 'IT']"
     * }</pre>
     *
     * @param predicate
     *            The predicate string containing placeholder variables to interpolate
     * @param prefix
     *            The variable prefix to look for (e.g., '@user.' or '@request.')
     * @param variableValues
     *            The BSON document containing the variable values to substitute
     * @return The interpolated predicate string with all variables replaced
     */
    static String interpolatePredicate(String predicate, String prefix, BsonDocument variableValues) {
        if (variableValues == null || variableValues.isEmpty()) {
            return predicate;
        }

        var flatten = BsonUtils.flatten(variableValues, true);

        String[] ret = { predicate };

        // Sort keys by length in descending order to replace longer paths first
        // This prevents @user._id from becoming 'user'._id when @user is replaced first
        var sortedKeys = flatten.keySet().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();

        // interpolate primitive values
        sortedKeys.stream().filter(key -> flatten.get(key) != null)
                .filter(key -> isJsonPrimitive(flatten.get(key)))
                .forEach(key -> ret[0] = ret[0].replaceAll(Pattern.quote(prefix.concat(key)),
                        quote(jsonPrimitiveValue(flatten.get(key)))));

        // interpolate arrays
        sortedKeys.stream().filter(key -> flatten.get(key) != null)
                .filter(key -> isJsonArray(flatten.get(key)))
                .forEach(key -> ret[0] = ret[0].replaceAll(Pattern.quote(prefix.concat(key)),
                        jsonArrayValue(flatten.get(key).asArray())));

        // remove unboud variables
        flatten.keySet().stream().forEach(key -> ret[0] = removeUnboundVariables(prefix, ret[0]));

        return ret[0];
    }

    private static boolean isJsonPrimitive(BsonValue value) {
        return value.isNull() || value.isBoolean() || value.isNumber() || value.isString() || value.isObjectId()
                || value.isTimestamp() || value.isDateTime();
    }

    private static boolean isJsonArray(BsonValue value) {
        return value.isArray();
    }

    /**
     * Converts a BSON primitive value to its string representation for use in predicates.
     *
     * <p>
     * This method handles the conversion of various BSON types to their appropriate
     * string representations for interpolation into Undertow predicates. String values
     * are returned as-is, while other types are converted to their string form.
     * </p>
     *
     * @param val
     *            The BSON primitive value to convert
     * @return The string representation of the value
     */
    private static String jsonPrimitiveValue(BsonValue value) {
        return switch (value.getBsonType()) {
            case NULL -> "null";
            case BOOLEAN -> value.asBoolean().toString();
            case INT32 -> "" + value.asInt32().getValue();
            case INT64 -> "" + value.asInt64().getValue();
            case DOUBLE -> "" + value.asDouble().getValue();
            case STRING -> value.asString().getValue();
            case OBJECT_ID -> value.asObjectId().getValue().toHexString();
            case DATE_TIME -> "" + value.asDateTime().getValue();
            case TIMESTAMP -> "" + value.asTimestamp().getValue();
            default -> throw new IllegalArgumentException(
                    "Cannot use in predicate field of type " + value.getBsonType());
        };
    }

    /**
     * Converts a BSON array to a comma-separated string suitable for use in predicates.
     *
     * <p>
     * This method transforms a BSON array into a format compatible with Undertow predicates,
     * where array elements are separated by commas without spaces.
     * </p>
     *
     * @param array
     *            The BSON array to convert
     * @return A comma-separated string representation of the array elements
     */
    private static String jsonArrayValue(BsonArray array) {
        var sb = new StringBuilder();
        sb.append("{");
        sb.append(array.stream().filter(e -> isJsonPrimitive(e)).map(e -> quote(jsonPrimitiveValue(e)))
                .collect(Collectors.joining(",")));
        sb.append("}");

        return sb.toString();
    }

    /**
     * Normalizes quoted strings in predicates by replacing quote characters with placeholders.
     * This converts both single and double-quoted strings to single-quoted strings with
     * internal quotes replaced by appropriate placeholders to avoid escaping issues.
     *
     * @param predicate The predicate string to normalize
     * @return The predicate with all quotes replaced by placeholders
     */
    private static String normalizeQuotes(String predicate) {
        if (predicate == null) {
            return predicate;
        }

        var result = new StringBuilder();
        var inDoubleQuotes = false;
        var inSingleQuotes = false;
        var currentString = new StringBuilder();

        for (int i = 0; i < predicate.length(); i++) {
            char c = predicate.charAt(i);
            char prevChar = i > 0 ? predicate.charAt(i - 1) : '\0';

            // Check if this quote is escaped
            boolean isEscaped = prevChar == '\\';

            if (c == '"' && !isEscaped && !inSingleQuotes) {
                if (inDoubleQuotes) {
                    // End of double-quoted string - convert to single quotes with placeholders
                    var escaped = currentString.toString()
                        .replace("'", SINGLE_QUOTE_PLACEHOLDER)
                        .replace("\"", DOUBLE_QUOTE_PLACEHOLDER);
                    result.append('\'').append(escaped).append('\'');
                    currentString.setLength(0);
                    inDoubleQuotes = false;
                } else {
                    // Start of double-quoted string
                    inDoubleQuotes = true;
                }
            } else if (c == '\'' && !isEscaped && !inDoubleQuotes) {
                if (inSingleQuotes) {
                    // End of single-quoted string - replace internal quotes with placeholders
                    var escaped = currentString.toString()
                        .replace("'", SINGLE_QUOTE_PLACEHOLDER)
                        .replace("\"", DOUBLE_QUOTE_PLACEHOLDER);
                    result.append('\'').append(escaped).append('\'');
                    currentString.setLength(0);
                    inSingleQuotes = false;
                } else {
                    // Start of single-quoted string
                    inSingleQuotes = true;
                }
            } else if (inDoubleQuotes || inSingleQuotes) {
                // Accumulate characters within quotes
                currentString.append(c);
            } else {
                // Outside any quotes
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * Placeholder strings used to replace quote characters in predicates.
     * This avoids escaping issues with Undertow's predicate parser.
     */
    private static final String SINGLE_QUOTE_PLACEHOLDER = "___RESTHEART_SINGLE_QUOTE___";
    private static final String DOUBLE_QUOTE_PLACEHOLDER = "___RESTHEART_DOUBLE_QUOTE___";

    /**
     * Wraps a string value in single quotes for use in predicates.
     * Replaces any quotes within the string with appropriate placeholders.
     *
     * @param s
     *            The string to quote
     * @return The string wrapped in single quotes with internal quotes replaced by placeholders
     */
    private static String quote(String s) {
        // Replace quotes with placeholders
        var escaped = s.replace("'", SINGLE_QUOTE_PLACEHOLDER).replace("\"", DOUBLE_QUOTE_PLACEHOLDER);
        return "'".concat(escaped).concat("'");
    }

    private static final String RUV_REGEX = "\\\\\"|\"(?:\\\\\"|[^\"])*\"|'(?:\\\\'|[^'])*'|(@placeholder[^)|^,]*)";

    /**
     * Random number generator for creating unique tokens during variable substitution.
     */
    private static final Random RND_GENERATOR = new Random();

    /**
     * Generates a unique random token for temporary variable substitution.
     *
     * <p>
     * This method creates a cryptographically random string that is extremely unlikely
     * to collide with any actual content in the predicate. Used during the variable
     * removal process to ensure safe string replacement.
     * </p>
     *
     * @return A unique random string token
     */
    private static String nextToken() {
        return new BigInteger(256, RND_GENERATOR).toString(Character.MAX_RADIX);
    }

    /**
     * remove the unbound variables from the predicate
     *
     * @param prefix
     *            the prefix such as @user, or @now
     * @param predicate
     *            the predicate
     * @return the predicate without the unbound variables
     */
    static String removeUnboundVariables(String prefix, String predicate) {
        // matches prefix until , or ) in a way that we can ignore matches that are inside quotes
        // inspired by https://stackoverflow.com/a/23667311/4481670
        // regex is \\"|"(?:\\"|[^"])*"|\\'|'(?:\\'|[^'])*'|(@user[^)|^,]*)
        // where @user is the prefix

        var regex = Pattern.compile(RUV_REGEX.replaceAll("@placeholder", prefix));

        var m = regex.matcher(predicate);

        var sb = new StringBuilder();

        while (m.find()) {
            // we ignore the strings delimited by "
            if (!m.group().startsWith("\"") && !m.group().startsWith("'")) {
                m.appendReplacement(sb, nextToken());
            }
        }

        m.appendTail(sb);

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static BsonValue fromProperties(Map<String, ? super Object> properties, String key) {
        if (key.contains(".")) {
            var first = key.substring(0, key.indexOf("."));
            var last = key.substring(key.indexOf(".") + 1);
            var subProperties = properties.get(first);

            if (subProperties != null && subProperties instanceof Map<?, ?>) {
                return fromProperties((Map<String, Object>) subProperties, last);
            } else if (subProperties != null && subProperties instanceof List<?>) {
                List<Object> list = (List<Object>) subProperties;

                // we have a list, the next subkey must be a number as in first.0 or first.0.b
                if (last.contains(".")) {
                    var next = last.substring(0, last.indexOf("."));

                    try {
                        var idx = Integer.parseInt(next);
                        var elementAtIdx = list.get(idx);
                        var afterNext = last.substring(last.indexOf(".") + 1);

                        if (elementAtIdx instanceof Map<?, ?>) {
                            return fromProperties((Map<String, Object>) elementAtIdx, afterNext);
                        } else {
                            LOGGER.warn("Key {} at {} matches an array but selected element is not an object", key,
                                    first);
                            return BsonNull.VALUE;
                        }
                    } catch (Throwable t) {
                        LOGGER.warn("Key {} at {} matches an array but following part is not a number", key, first);
                        return BsonNull.VALUE;
                    }
                } else {
                    try {
                        var idx = Integer.parseInt(last);
                        return toBson(list.get(idx));
                    } catch (Throwable t) {
                        LOGGER.error("Key {} at {} matches an array but following part is not a number", key, first);
                        return BsonNull.VALUE;
                    }
                }
            } else {
                return BsonNull.VALUE;
            }
        } else {
            if (properties.containsKey(key)) {
                return toBson(properties.get(key));
            } else {
                return BsonNull.VALUE;
            }
        }
    }

    private static BsonValue toBson(Object obj) {
        if (obj == null) {
            return BsonNull.VALUE;
        }

        return switch (obj) {
            case String s -> new BsonString(s);
            case Map<?, ?> map -> {
                var ret = new BsonDocument();
                map.entrySet().stream()
                        .filter(e -> e.getKey() instanceof String)
                        .forEachOrdered(e -> ret.put((String) e.getKey(), toBson(e.getValue())));
                yield ret;
            }
            case List<?> list -> {
                var ret = new BsonArray();
                list.stream().forEachOrdered(e -> ret.add(toBson(e)));
                yield ret;
            }
            case Integer i -> new BsonInt32(i);
            case Long l -> new BsonInt64(l);
            case Double d -> new BsonDouble(d);
            case Boolean b -> new BsonBoolean(b);
            default -> {
                LOGGER.warn("Cannot convert value {} to BSON, the type {} is not supported", obj,
                        obj.getClass().getSimpleName());
                yield BsonNull.VALUE;
            }
        };
    }

    private static BsonDocument getRequestObject(final MongoRequest request) {
        var exchange = request.getExchange();

        var properties = new BsonDocument();

        // the name of the db
        properties.put("db", request.getDBName() == null
            ? BsonNull.VALUE
            : new BsonString(request.getDBName()));

        // the name of the collection
        properties.put("collection", request.getDBName() == null
            ? BsonNull.VALUE
            : new BsonString(request.getCollectionName()));

        // the _id of the document
        properties.put("_id", request.getDocumentId() == null
            ? BsonNull.VALUE
            : request.getDocumentId());

        // the TYPE of the resource:
        // - INVALID, ROOT, ROOT_SIZE, DB, DB_SIZE, DB_META, CHANGE_STREAM, COLLECTION,
        // - COLLECTION_SIZE, COLLECTION_META, DOCUMENT, COLLECTION_INDEXES, INDEX,
        // - FILES_BUCKET, FILES_BUCKET_SIZE, FILES_BUCKET_META, FILE, FILE_BINARY,
        // - AGGREGATION, SCHEMA, SCHEMA_STORE, SCHEMA_STORE_SIZE, SCHEMA_STORE_META,
        // - BULK_DOCUMENTS, SESSION, SESSIONS, TRANSACTIONS, TRANSACTION
        properties.put("resourceType", new BsonString(request.getType().name()));

        var _userName = ExchangeAttributes.remoteUser().readAttribute(exchange);

        var userName = _userName != null
            ? new BsonString(_userName)
            : BsonNull.VALUE;

        // remote user
        properties.put("userName", userName);

        // dateTime
        properties.put("epochTimeStamp", new BsonDateTime(Instant.now().getEpochSecond() * 1000));

        // dateTime
        properties.put("dateTime", new BsonString(ExchangeAttributes.dateTime().readAttribute(exchange)));

        // local ip
        properties.put("localIp", new BsonString(ExchangeAttributes.localIp().readAttribute(exchange)));

        // local port
        properties.put("localPort", new BsonString(ExchangeAttributes.localPort().readAttribute(exchange)));

        // local server name
        properties.put("localServerName", new BsonString(ExchangeAttributes.localServerName().readAttribute(exchange)));

        // request query string
        properties.put("queryString", new BsonString(ExchangeAttributes.queryString().readAttribute(exchange)));

        // request relative path
        properties.put("relativePath", new BsonString(ExchangeAttributes.relativePath().readAttribute(exchange)));

        // remote ip
        properties.put("remoteIp", new BsonString(ExchangeAttributes.remoteIp().readAttribute(exchange)));

        // request method
        properties.put("method", new BsonString(ExchangeAttributes.requestMethod().readAttribute(exchange)));

        // request protocol
        properties.put("protocol", new BsonString(ExchangeAttributes.requestProtocol().readAttribute(exchange)));

        return properties;
    }
}
