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
 * =========================LICENSE_END==================================
 */
package org.restheart.security.analysis;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.security.BaseAclPermission;
import org.restheart.security.EvaluationScope.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code mcp} block of an ACL permission: what its author can tell the catalog that the
 * permission itself does not say.
 *
 * <pre>
 * {
 *   "predicate": "path-prefix('/orders') and is-premium() and equals(%{q,page}, '1')",
 *   "roles": ["customer"],
 *   "mcp": { "resolve": { "is-premium": "listing" }, "publish": "never" }
 * }
 * </pre>
 *
 * <p><strong>{@code resolve}</strong> gives an opaque atom the rule its author never declared, with
 * the same words as {@link Scope}: {@code listing} — it reads only the session, so it is evaluated
 * — or {@code call}, which leaves it undetermined. It is a stopgap for a predicate coming from a
 * library that cannot be changed; the place the rule belongs is the builder that registers it.
 *
 * <p><strong>{@code publish: never}</strong> keeps a permission out of the catalog's reasoning
 * altogether — for the deliberately wide one an operations role holds, which would otherwise make
 * every resource visible to it. There is no {@code always}: showing is already what happens when
 * nothing is known.
 */
public final class PermissionHints {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionHints.class);

    private PermissionHints() {
    }

    /** Whether this permission takes part in deciding a catalog at all. */
    public static boolean publishes(BaseAclPermission permission) {
        return !"never".equalsIgnoreCase(string(mcp(permission), "publish"));
    }

    /**
     * The rules this permission declares for atoms nobody else does, by predicate name.
     *
     * <p>A word that is neither {@code listing} nor {@code call} is ignored with a warning: taking
     * a guess at what it meant is how a declaration ends up doing the opposite of what was written.
     */
    public static Map<String, Scope> resolved(BaseAclPermission permission) {
        var mcp = mcp(permission);

        if (mcp == null || !(mcp.get("resolve") instanceof BsonDocument declared)) {
            return Map.of();
        }

        var scopes = new LinkedHashMap<String, Scope>();

        declared.forEach((name, value) -> {
            var word = value instanceof BsonString s ? s.getValue() : null;

            if ("listing".equalsIgnoreCase(word)) {
                scopes.put(name, Scope.LISTING);
            } else if ("call".equalsIgnoreCase(word)) {
                scopes.put(name, Scope.CALL);
            } else {
                LOGGER.warn("mcp.resolve declares '{}' as '{}', which is neither 'listing' nor 'call': ignored",
                        name, word);
            }
        });

        return scopes;
    }

    private static BsonDocument mcp(BaseAclPermission permission) {
        if (permission == null || !(permission.getRaw() instanceof BsonDocument raw)) {
            return null;
        }

        return raw.get("mcp") instanceof BsonDocument mcp ? mcp : null;
    }

    private static String string(BsonDocument doc, String key) {
        return doc != null && doc.get(key) instanceof BsonString s ? s.getValue() : null;
    }
}
