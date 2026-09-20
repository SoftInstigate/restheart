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

import java.util.Set;

import org.restheart.security.AclVarsInterpolator;
import org.restheart.security.EvaluationScope.Scope;

/**
 * What is already determined when a catalog listing is composed.
 *
 * <p>One place, because three readers need the same answer and three copies of it would drift:
 * {@link ListingEvaluator} resolves the candidate resource's own attributes, a
 * {@link ListingContext} resolves the session's, and whoever validates a declared condition has to
 * know which of the two an atom is asking for before anything is evaluated.
 */
public final class Determined {

    private Determined() {
    }

    /** Attributes that speak of the resource being considered, which the evaluator supplies. */
    public static final Set<String> CANDIDATE_ATTRIBUTES = Set.of(
            "%{RELATIVE_PATH}", "%{REQUEST_PATH}", "%{REQUEST_URL}", "%{REQUEST_URI}", "%R",
            "%m", "%{METHOD}");

    /** Attributes that speak of the session asking, which a context reads off the live request. */
    public static final Set<String> SESSION_ATTRIBUTES = Set.of(
            "%u", "%{REMOTE_USER}", "%a", "%{REMOTE_IP}");

    /**
     * Whether one written argument is <em>known</em>: it has a value before the call exists.
     *
     * <p>{@code 'gold'} is, and so is {@code @user.plan} when its resolver says it reads only the
     * session. {@code %{q,page}} is not, and neither is {@code @qparams['id']}: there is no call
     * yet to read them from, so an atom containing one is undetermined however its predicate is
     * declared.
     *
     * @param token  the argument as written
     * @param quoted whether it was written quoted, which makes it a literal whatever it looks like
     */
    public static boolean isKnown(String token, boolean quoted) {
        if (quoted || token == null) {
            return true;
        }

        if (token.startsWith("${")) {
            return true;
        }

        if (token.startsWith("%")) {
            return CANDIDATE_ATTRIBUTES.contains(token) || SESSION_ATTRIBUTES.contains(token);
        }

        if (token.startsWith("@")) {
            return AclVarsInterpolator.scopeOfVar(token).orElse(Scope.CALL) == Scope.LISTING;
        }

        return true;
    }
}
