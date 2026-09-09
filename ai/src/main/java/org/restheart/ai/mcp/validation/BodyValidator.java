/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.ai.mcp.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.everit.json.schema.CombinedSchema;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Validates a {@code how_to_call} {@code body} argument against the inline JSON
 * Schema declared by an action's {@code body_schema}.
 *
 * <p>This is a different validation shape from RESTHeart's {@code JsonSchemas}
 * SPI (commons {@code org.restheart.plugins.schema}): that one validates a
 * document against a schema stored <em>by reference</em> in the {@code _schemas}
 * collection, for an actual MongoDB write. Here there is no stored schema and no
 * write — {@code body_schema} is an inline JSON Schema object already present in
 * the {@code McpResource}. So this delegates directly to the same underlying
 * library RESTHeart already uses for JSON Schema (everit-org.json-schema, see
 * {@code JsonMetaSchemaChecker} in restheart-mongodb) rather than to an existing
 * RESTHeart class — no class in the codebase does "validate an arbitrary value
 * against an arbitrary inline schema" today.
 */
public final class BodyValidator {

    /**
     * A body that matches no branch of a large {@code oneOf} produces one violation per branch per
     * property; the error stays useful only if it is bounded.
     */
    private static final int MAX_REPORTED_VIOLATIONS = 20;

    private BodyValidator() {
    }

    /**
     * @param bodySchema the action's {@code body_schema} (may be {@code null} or empty — nothing to validate)
     * @param body       the candidate value, typically {@code args.get("body")}
     * @return validation error messages, empty if valid (or if there is no schema to validate against)
     */
    public static List<String> validate(Map<String, Object> bodySchema, Object body) {
        if (bodySchema == null || bodySchema.isEmpty()) {
            return List.of();
        }

        try {
            var schema = SchemaLoader.load(new JSONObject(bodySchema));
            schema.validate(toJsonValue(body));
            return List.of();
        } catch (ValidationException ve) {
            return violations(ve);
        }
    }

    /**
     * Renders a {@link ValidationException} as one message per actual violation, each carrying the
     * JSON pointer of the offending property.
     * <p>
     * Everit reports a violation as a tree: the exception at the root of a failed {@code oneOf}
     * says only how many subschemas matched, and the reason lives in its causing exceptions,
     * recursively. Reporting the root plus one level — which is what this used to do — told an
     * agent {@code "#: only 1 subschema matches out of 2; #: #: 0 subschemas matched instead of
     * one"}: no property, no constraint, and the same text for two entirely different mistakes.
     * Since composing a valid body is precisely what this tool exists to help with, that left the
     * caller with nothing to act on but trial and error.
     * <p>
     * Walking to the leaves instead yields {@code "#/give/qty: expected maximum: 3, found: 9"}.
     * <p>
     * The same walk exists in {@code JsonSchemasImpl} in restheart-mongodb, which reports the
     * identical failure for a REST write. It is duplicated rather than shared because it needs
     * everit, and restheart-commons does not depend on it — putting it there would push the
     * dependency onto every module. Keep the two in step.
     */
    private static List<String> violations(ValidationException ve) {
        var leaves = new ArrayList<String>();
        collectLeaves(ve, leaves);

        // Guards against a schema that genuinely repeats the same constraint; branch grouping
        // above already keeps each alternative's reasons together, so nothing meaningful collapses.
        var violations = leaves.stream().distinct().toList();

        if (violations.size() > MAX_REPORTED_VIOLATIONS) {
            var reported = new ArrayList<>(violations.subList(0, MAX_REPORTED_VIOLATIONS));
            reported.add("and " + (violations.size() - MAX_REPORTED_VIOLATIONS) + " more violations");
            return reported;
        }

        return violations;
    }

    private static void collectLeaves(ValidationException ve, List<String> into) {
        collectLeaves(ve, into, true);
    }

    /**
     * @param mayGroup group by alternative only at the outermost combiner. A schema can nest a
     *                 {@code oneOf} inside a branch of another (here: goods-vs-coin inside the
     *                 "offer" event), and grouping at every level produced
     *                 {@code "alternative 1 does not match: alternative 1 does not match: …"} —
     *                 nesting that says nothing, since the reader is choosing among the outer
     *                 alternatives. Inside a branch the reasons are simply listed.
     */
    private static void collectLeaves(ValidationException ve, List<String> into, boolean mayGroup) {
        var causes = ve.getCausingExceptions();

        if (causes.isEmpty()) {
            into.add(leaf(ve));
        } else if (mayGroup && ve.getViolatedSchema() instanceof CombinedSchema combined && causes.size() > 1) {
            // oneOf/anyOf/allOf: the branches are ALTERNATIVES, so flattening their leaves into one
            // list produces an impossible demand — the union of every branch's required keys, which
            // no valid document has. Keep each branch's reasons together and say which branch they
            // belong to, using the subschema's own "title" when the schema author set one.
            var alternatives = List.copyOf(combined.getSubschemas());

            for (var i = 0; i < causes.size(); i++) {
                var branch = causes.get(i);
                var reasons = new ArrayList<String>();
                collectLeaves(branch, reasons, false);
                into.add(branchLabel(branch, alternatives, i) + " does not match: "
                        + String.join("; ", reasons.stream().distinct().toList()));
            }
        } else {
            causes.forEach(cause -> collectLeaves(cause, into, mayGroup));
        }
    }

    private static String leaf(ValidationException ve) {
        var pointer = ve.getPointerToViolation();
        // getErrorMessage() is the keyword's message without the pointer everit prepends in
        // getMessage(); prepending it once here is what stops the "#: #: " doubling.
        return pointer == null || "#".equals(pointer)
                ? ve.getErrorMessage()
                : pointer + ": " + ve.getErrorMessage();
    }

    private static String branchLabel(ValidationException branch, List<Schema> alternatives, int index) {
        var title = titleOf(alternativeOf(branch, alternatives));

        if (title == null) {
            title = titleOf(branch.getViolatedSchema());
        }

        return title == null ? "alternative " + (index + 1) : "alternative '" + title + "'";
    }

    private static String titleOf(Schema schema) {
        var title = schema == null ? null : schema.getTitle();
        return title == null || title.isBlank() ? null : title;
    }

    /**
     * Finds which subschema of a combiner a branch failure came from.
     * <p>
     * The failure's own {@code violatedSchema} is not reliably the branch: when a branch fails
     * inside a nested combiner — an {@code offer} whose {@code give} is itself a {@code oneOf} —
     * everit propagates the inner exception, whose violated schema is the inner combiner and
     * carries no {@code title}. The causing exceptions are not in declaration order either, so the
     * index cannot be trusted (in practice they come back offer, genesis, claim, trade for a schema
     * that declares genesis, offer, trade, claim).
     * <p>
     * What is reliable is the location: each subschema knows its own place in the schema document
     * ({@code #/oneOf/1}) and the failure carries a location beneath it
     * ({@code #/oneOf/1/properties/give}). The branch is therefore the subschema whose location the
     * failure's location extends. Longest match wins, and the segment-boundary check stops
     * {@code #/oneOf/1} from claiming {@code #/oneOf/10}.
     */
    private static Schema alternativeOf(ValidationException branch, List<Schema> alternatives) {
        var location = branch.getSchemaLocation();

        if (location == null) {
            return null;
        }

        Schema best = null;

        for (var alternative : alternatives) {
            var at = alternative.getSchemaLocation();

            if (at == null || !location.startsWith(at)) {
                continue;
            }

            if (location.length() > at.length() && location.charAt(at.length()) != '/') {
                continue;
            }

            if (best == null || at.length() > best.getSchemaLocation().length()) {
                best = alternative;
            }
        }

        return best;
    }

    private static Object toJsonValue(Object value) {
        return switch (value) {
            case null -> JSONObject.NULL;
            case Map<?, ?> m -> new JSONObject(m);
            case List<?> l -> new JSONArray(l);
            default -> value;
        };
    }
}
