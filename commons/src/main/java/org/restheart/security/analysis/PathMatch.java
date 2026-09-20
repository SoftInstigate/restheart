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
package org.restheart.security.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map;
import java.util.Optional;

/**
 * Compares a permission's path expression with the resource a listing is considering.
 *
 * <p>The resource is an input here, not an unknown: the catalog walks what it has and asks the
 * permissions about each one, so a path atom — a literal, a prefix, a template, a regular
 * expression — is simply applied.
 *
 * <p>The candidate may itself be templated: a catalog holds {@code /coll/{id}} as an entry in its
 * own right. Comparing a variable with a literal decides nothing, and the honest answer is
 * {@link Truth#UNDETERMINED}, which shows the resource.
 */
final class PathMatch {

    private PathMatch() {
    }

    /** A template match, with whatever it bound along the way. */
    record TemplateMatch(Truth truth, Map<String, Optional<String>> bindings) {
    }

    static Truth equals(String pattern, String candidate) {
        var p = segments(trim(pattern));
        var c = segments(trim(candidate));

        if (p.length != c.length) {
            return Truth.FALSE;
        }

        var result = Truth.TRUE;

        for (var i = 0; i < p.length; i++) {
            result = result.and(sameSegment(p[i], c[i]));
        }

        return result;
    }

    static Truth prefix(String pattern, String candidate) {
        // the root prefix is the one an owner writes to mean "everything", and it has no segments
        if (trim(pattern).isEmpty() || "/".equals(trim(pattern))) {
            return Truth.TRUE;
        }

        var p = segments(trim(pattern));
        var c = segments(trim(candidate));

        if (p.length > c.length) {
            return Truth.FALSE;
        }

        var result = Truth.TRUE;

        for (var i = 0; i < p.length; i++) {
            result = result.and(sameSegment(p[i], c[i]));
        }

        return result;
    }

    static Truth suffix(String pattern, String candidate) {
        if (isVariable(candidate)) {
            return Truth.UNDETERMINED;
        }

        return Truth.of(trim(candidate).endsWith(pattern));
    }

    /**
     * A path template, which answers and binds: {@code /t-{tenant}/orders} against
     * {@code /t-acme/orders} is true with {@code tenant} bound to {@code acme}, so a later
     * {@code equals(@user.tenant, ${tenant})} is decided exactly.
     *
     * <p>A variable does not have to be a whole segment: {@code t-{tenant}} binds the part after
     * the prefix, as the path template of a route does.
     */
    static TemplateMatch template(String pattern, String candidate) {
        var p = segments(trim(pattern));
        var c = segments(trim(candidate));
        var bindings = new LinkedHashMap<String, Optional<String>>();

        if (p.length != c.length) {
            return new TemplateMatch(Truth.FALSE, bindings);
        }

        var result = Truth.TRUE;

        for (var i = 0; i < p.length; i++) {
            result = result.and(matchSegment(p[i], c[i], bindings));
        }

        return new TemplateMatch(result, bindings);
    }

    /**
     * One templated segment against one candidate segment, binding what it names.
     *
     * <p>A candidate that is itself a variable — the catalog holds {@code /coll/{id}} as an entry —
     * decides nothing and binds nothing to a value.
     */
    private static Truth matchSegment(String pattern, String candidate, Map<String, Optional<String>> bindings) {
        var open = pattern.indexOf('{');

        if (open < 0) {
            return sameSegment(pattern, candidate);
        }

        var names = new ArrayList<String>();
        var regex = new StringBuilder("^");
        var at = 0;

        while (at < pattern.length()) {
            var start = pattern.indexOf('{', at);

            if (start < 0) {
                regex.append(java.util.regex.Pattern.quote(pattern.substring(at)));
                break;
            }

            var end = pattern.indexOf('}', start);

            if (end < 0) {
                return sameSegment(pattern, candidate);
            }

            regex.append(java.util.regex.Pattern.quote(pattern.substring(at, start))).append("(.+)");
            names.add(pattern.substring(start + 1, end));
            at = end + 1;
        }

        regex.append("$");

        if (isVariable(candidate)) {
            names.forEach(name -> bindings.put(name, Optional.empty()));
            return Truth.UNDETERMINED;
        }

        var matcher = java.util.regex.Pattern.compile(regex.toString()).matcher(candidate);

        if (!matcher.matches()) {
            return Truth.FALSE;
        }

        for (var i = 0; i < names.size(); i++) {
            bindings.put(names.get(i), Optional.of(matcher.group(i + 1)));
        }

        return Truth.TRUE;
    }

    /** Two segments compared, where a variable on the candidate's side decides nothing. */
    private static Truth sameSegment(String pattern, String candidate) {
        if (isVariable(candidate)) {
            return Truth.UNDETERMINED;
        }

        return Truth.of(pattern.equals(candidate));
    }

    private static boolean isVariable(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    private static String trim(String path) {
        var p = path == null ? "" : path;

        return p.length() > 1 && p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    private static String[] segments(String path) {
        return path.startsWith("/") ? path.substring(1).split("/", -1) : path.split("/", -1);
    }
}
