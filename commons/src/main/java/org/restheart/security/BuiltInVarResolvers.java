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
package org.restheart.security;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.jsonpath.JsonPath;

/**
 * The built-in {@link VarResolver}s, migrated 1:1 from {@link AclVarsInterpolator}'s former
 * hardcoded {@code if/else} chain. Registered by {@link AclVarsRegistryImpl}'s constructor,
 * before any plugin can register a competing name.
 */
final class BuiltInVarResolvers {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuiltInVarResolvers.class);

    private BuiltInVarResolvers() {
    }

    static List<VarResolver> all() {
        return List.of(
                new UserVarResolver(),
                new RequestVarResolver(),
                new FilterVarResolver(),
                new NowVarResolver(),
                new MongoPermissionsVarResolver(),
                new RndVarResolver(),
                new RolesVarResolver(),
                new AuthenticatedVarResolver(),
                new QparamsVarResolver());
    }

    /** {@code @user}, {@code @user.<property>} — the authenticated account. */
    private static final class UserVarResolver implements VarResolver {
        @Override
        public String name() {
            return "user";
        }

        @Override
        public BsonValue resolve(Request<?> request, String var) {
            if (request == null || request.getAuthenticatedAccount() == null) {
                return BsonNull.VALUE;
            }

            if (var.equals("@user")) {
                return switch (request.getAuthenticatedAccount()) {
                    case MongoRealmAccount maccount -> maccount.properties();
                    case FileRealmAccount faccount -> AclVarsInterpolator.toBson(faccount.properties());
                    case JwtAccount jwtAccount -> {
                        // remove pure JWT bookkeeping (expiry, issuer) but keep "sub": it's the
                        // user identity, and predicates commonly need
                        // equals(@user._id, ...) or equals(@user.sub, ...) to cover both
                        // MongoRealmAccount/FileRealmAccount (_id) and JwtAccount (sub).
                        var jwt = jwtAccount.propertiesAsMap();
                        jwt.remove("exp");
                        jwt.remove("iss");
                        yield AclVarsInterpolator.toBson(jwt);
                    }
                    default -> BsonNull.VALUE;
                };
            } else if (var.startsWith("@user.") && var.length() > 5) {
                if (request.getAuthenticatedAccount() instanceof WithProperties<?> accountWithProperties) {
                    return AclVarsInterpolator.fromProperties(accountWithProperties.propertiesAsMap(), var.substring(6));
                }
                return BsonNull.VALUE;
            }

            return BsonNull.VALUE;
        }
    }

    /**
     * {@code @request}, {@code @request.<property>}, {@code @request.body},
     * {@code @request.body.<path>}.
     *
     * <p>The metadata form ({@code @request}/{@code @request.<property>}) only makes sense for a
     * {@link MongoRequest} (it exposes db/collection/resourceType/... via
     * {@link AclVarsInterpolator#getRequestObject(MongoRequest)}) and resolves to
     * {@link BsonNull#VALUE} otherwise. The body form works for any {@link Request} whose content
     * is a document, via {@link AclVarsInterpolator#getRequestBodyDocument(Request)}.
     */
    private static final class RequestVarResolver implements VarResolver {
        @Override
        public String name() {
            return "request";
        }

        @Override
        public BsonValue resolve(Request<?> request, String var) {
            if (request == null) {
                return BsonNull.VALUE;
            }

            if (var.equals("@request.body") || (var.startsWith("@request.body.") && var.length() > 13)) {
                return resolveBody(request, var);
            }

            if (!(request instanceof MongoRequest mongoRequest)) {
                return BsonNull.VALUE;
            }

            var requestObject = AclVarsInterpolator.getRequestObject(mongoRequest);

            if (var.equals("@request")) {
                return requestObject;
            } else if (var.startsWith("@request.") && var.length() > 8) {
                var prop = var.substring(9);

                if (prop.contains(".")) {
                    try {
                        var v = JsonPath.read(requestObject.toJson(), "$.".concat(prop));
                        return BsonUtils.parse(v.toString());
                    } catch (Throwable pnfe) {
                        return BsonNull.VALUE;
                    }
                } else {
                    return requestObject.containsKey(prop) ? requestObject.get(prop) : BsonNull.VALUE;
                }
            }

            return BsonNull.VALUE;
        }

        private static BsonValue resolveBody(Request<?> request, String var) {
            if (var.equals("@request.body")) {
                if (request instanceof BsonRequest bsonRequest) {
                    try {
                        var content = bsonRequest.getContent();
                        return content == null ? BsonNull.VALUE : content;
                    } catch (Throwable t) {
                        LOGGER.debug("Error getting request content", t);
                        return BsonNull.VALUE;
                    }
                }

                try {
                    var doc = AclVarsInterpolator.getRequestBodyDocument(request);
                    return doc == null ? BsonNull.VALUE : doc;
                } catch (BadRequestException bre) {
                    return BsonNull.VALUE;
                }
            }

            final BsonDocument contentDoc;
            try {
                contentDoc = AclVarsInterpolator.getRequestBodyDocument(request);
            } catch (BadRequestException bre) {
                return BsonNull.VALUE;
            }

            if (contentDoc == null) {
                return BsonNull.VALUE;
            }

            var prop = var.substring(14);

            if (prop.contains(".")) {
                return BsonUtils.get(contentDoc, prop).orElse(BsonNull.VALUE);
            } else {
                return contentDoc.containsKey(prop) ? contentDoc.get(prop) : BsonNull.VALUE;
            }
        }
    }

    /** {@code @filter} — the current MongoDB filter document. */
    private static final class FilterVarResolver implements VarResolver {
        @Override
        public String name() {
            return "filter";
        }

        @Override
        public BsonValue resolve(Request<?> request, String var) {
            return request instanceof MongoRequest mongoRequest ? mongoRequest.getFiltersDocument() : BsonNull.VALUE;
        }
    }

    /** {@code @now} — current timestamp. Cacheable: the same instant for the whole request. */
    private static final class NowVarResolver implements VarResolver {
        @Override
        public String name() {
            return "now";
        }

        @Override
        public BsonValue resolve(Request<?> request, String var) {
            return new BsonDateTime(Instant.now().getEpochSecond() * 1000);
        }
    }

    /** {@code @mongoPermissions}, {@code @mongoPermissions.<property>}. */
    private static final class MongoPermissionsVarResolver implements VarResolver {
        @Override
        public String name() {
            return "mongoPermissions";
        }

        @Override
        public BsonValue resolve(Request<?> request, String var) {
            var permissions = MongoPermissions.of(request);
            if (permissions == null) {
                return BsonNull.VALUE;
            }

            var doc = permissions.asBson();

            if (var.equals("@mongoPermissions")) {
                return doc;
            } else if (var.startsWith("@mongoPermissions.") && var.length() > 17) {
                var prop = var.substring(18);

                if (prop.contains(".")) {
                    try {
                        var v = JsonPath.read(doc.toJson(), "$.".concat(prop));
                        return BsonUtils.parse(v.toString());
                    } catch (Throwable pnfe) {
                        return BsonNull.VALUE;
                    }
                } else {
                    return doc.containsKey(prop) ? doc.get(prop) : BsonNull.VALUE;
                }
            }

            return BsonNull.VALUE;
        }
    }

    /**
     * {@code @rnd(bits)} — cryptographically secure random hex string. Not cacheable: every
     * occurrence must yield an independent value.
     */
    private static final class RndVarResolver implements VarResolver {
        @Override
        public String name() {
            return "rnd";
        }

        @Override
        public boolean cacheable() {
            return false;
        }

        @Override
        public BsonValue resolve(Request<?> request, String var) {
            if (!var.endsWith(")")) {
                return BsonNull.VALUE;
            }

            try {
                var bitsStr = var.substring(5, var.length() - 1);
                var bits = Integer.parseInt(bitsStr);

                if (bits <= 0 || bits > 4096) {
                    LOGGER.warn("@rnd() bit length must be between 1 and 4096, got: {}", bits);
                    return BsonNull.VALUE;
                }

                var bytes = new byte[(bits + 7) / 8]; // Convert bits to bytes
                new SecureRandom().nextBytes(bytes);
                var hex = new BigInteger(1, bytes).toString(16);

                // Pad with leading zeros if necessary
                while (hex.length() < bytes.length * 2) {
                    hex = "0" + hex;
                }

                return new BsonString(hex);
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid @rnd() syntax: {}", var);
                return BsonNull.VALUE;
            }
        }
    }

    /** {@code @qparams['key']}, {@code @qparams["key"]} — a query parameter value. */
    /**
     * {@code @roles} — the roles of whoever is making the request, as an array,
     * or {@code ["$unauthenticated"]} when nobody is.
     *
     * <p>Fills a gap that is easy to miss until it costs something: a rule keyed
     * on a user's state runs the same comparison for a caller who has no user,
     * finds it false, and acts. A permission can say {@code roles: [...]} and be
     * done; a predicate had no way to ask.
     *
     * <pre>
     * # applies only to callers holding a given role
     * in(value='staff', array=@roles)
     * </pre>
     *
     * <p>{@code $unauthenticated} is the name {@code mongoAclAuthorizer} already
     * uses for a request with no account, and it is what appears here, so
     * permissions and predicates speak of anonymity in one vocabulary.
     *
     * <p><b>It cannot be compared in a predicate, though</b> — {@code $} is
     * Undertow's sigil for an exchange attribute, so {@code value='$unauthenticated'}
     * is read as an attribute of that name, resolves to nothing, and matches
     * nothing. Use {@link AuthenticatedVarResolver @authenticated} to ask whether
     * anyone is signed in.
     *
     * <p>An authenticated account holding no roles resolves to an empty array,
     * not to {@code $unauthenticated}. The two are different — an API key naming
     * no roles is a credential, and a rule written for guests must not apply to
     * it.
     */
    private static final class RolesVarResolver implements VarResolver {
        private static final String UNAUTHENTICATED = "$unauthenticated";

        @Override
        public String name() {
            return "roles";
        }

        @Override
        public BsonValue resolve(Request<?> request, String var) {
            var roles = new BsonArray();

            if (request == null || !request.isAuthenticated()) {
                roles.add(new BsonString(UNAUTHENTICATED));
                return roles;
            }

            var account = request.getAuthenticatedAccount();

            if (account != null && account.getRoles() != null) {
                account.getRoles().forEach(r -> roles.add(new BsonString(r)));
            }

            return roles;
        }
    }

    /**
     * {@code @authenticated} — {@code 'true'} when the request carries an
     * account, {@code 'false'} when it does not.
     *
     * <pre>
     * # applies only to callers who are signed in
     * equals(@authenticated, 'true')
     *
     * # applies only to anonymous ones
     * equals(@authenticated, 'false')
     * </pre>
     *
     * <p>Exists because {@code @roles} cannot answer this one. The role for a
     * request with no account is {@code $unauthenticated}, and {@code $} is
     * Undertow's sigil for an exchange attribute: written into a predicate it is
     * read as an attribute of that name, resolves to nothing, and the comparison
     * quietly fails. A rule guarded that way applies to everybody, which is the
     * opposite of what it says.
     *
     * <p>A string rather than a boolean because the predicate language compares
     * attributes, and every attribute is text.
     */
    private static final class AuthenticatedVarResolver implements VarResolver {
        @Override
        public String name() {
            return "authenticated";
        }

        @Override
        public BsonValue resolve(Request<?> request, String var) {
            return new BsonString(request != null && request.isAuthenticated() ? "true" : "false");
        }
    }

    private static final class QparamsVarResolver implements VarResolver {
        @Override
        public String name() {
            return "qparams";
        }

        @Override
        public BsonValue resolve(Request<?> request, String var) {
            final String paramName;

            if (var.startsWith("@qparams['") && var.endsWith("']")) {
                paramName = var.substring(10, var.length() - 2);
            } else if (var.startsWith("@qparams[\"") && var.endsWith("\"]")) {
                paramName = var.substring(10, var.length() - 2);
            } else {
                return BsonNull.VALUE;
            }

            if (request == null) {
                return BsonNull.VALUE;
            }

            var exchange = request.getExchange();
            if (exchange == null) {
                return BsonNull.VALUE;
            }

            var queryParams = exchange.getQueryParameters();

            if (queryParams != null && queryParams.containsKey(paramName)) {
                var values = queryParams.get(paramName);
                if (values != null && !values.isEmpty()) {
                    // Return first value if multiple exist
                    return new BsonString(values.getFirst());
                }
            }

            return BsonNull.VALUE;
        }
    }
}
