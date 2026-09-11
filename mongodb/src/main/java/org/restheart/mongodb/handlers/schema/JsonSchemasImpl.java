/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.handlers.schema;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.everit.json.schema.CombinedSchema;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import org.restheart.plugins.schema.JsonSchemaNotFoundException;
import org.restheart.plugins.schema.JsonSchemas;
import org.restheart.plugins.schema.SchemaValidationException;
import org.restheart.utils.BsonUtils;

/**
 * Implementation of {@link JsonSchemas} that delegates to
 * {@link JsonSchemaCacheSingleton} for caching and loading schemas.
 * <p>
 * Everit types never cross this class boundary — the API exposes only
 * {@link String} (raw JSON) and {@link SchemaValidationException}.
 */
public class JsonSchemasImpl implements JsonSchemas {

    /**
     * A document that matches no branch of a large {@code oneOf} produces one violation per branch
     * per property; the response stays useful only if it is bounded.
     */
    private static final int MAX_REPORTED_VIOLATIONS = 20;

    // Lazy: MongoServiceConfiguration is not ready at provider-registration time
    private JsonSchemaCacheSingleton cache() {
        return JsonSchemaCacheSingleton.getInstance();
    }

    @Override
    public void validate(BsonDocument doc, String schemaStoreDb, BsonValue schemaId)
            throws SchemaValidationException, JsonSchemaNotFoundException {
        validate(schema(schemaStoreDb, schemaId), doc);
    }

    @Override
    public void validate(List<BsonDocument> docs, String schemaStoreDb, BsonValue schemaId)
            throws SchemaValidationException, JsonSchemaNotFoundException {
        // resolve the schema once: with schema-cache-enabled=false this saves
        // one mongo read plus one SchemaLoader.load() per document
        var schema = schema(schemaStoreDb, schemaId);

        for (var doc : docs) {
            validate(schema, doc);
        }
    }

    private org.everit.json.schema.Schema schema(String schemaStoreDb, BsonValue schemaId)
            throws JsonSchemaNotFoundException {
        try {
            return cache().get(schemaStoreDb, schemaId);
        } catch (org.restheart.mongodb.handlers.schema.JsonSchemaNotFoundException ex) {
            throw new JsonSchemaNotFoundException(ex.getMessage(), ex);
        }
    }

    private void validate(org.everit.json.schema.Schema schema, BsonDocument doc)
            throws SchemaValidationException {
        // the document is always rendered with the default json mode: validation
        // must not depend on the jsonMode of the request that triggered it
        try {
            schema.validate(new JSONObject(BsonUtils.toJson(doc)));
        } catch (ValidationException ve) {
            var violations = violations(ve);

            throw new SchemaValidationException(
                    "Document violates schema: " + String.join(", ", violations),
                    violations,
                    ve);
        }
    }

    /**
     * Renders a {@link ValidationException} as one message per actual violation, each carrying the
     * JSON pointer of the offending property.
     * <p>
     * Everit reports a violation as a tree: the exception at the root of a failed {@code oneOf} or
     * {@code allOf} says only how many subschemas matched, and the reason lives in its causing
     * exceptions, recursively. Reporting the root plus one level — which is what this used to do —
     * therefore produced messages such as
     * {@code "only 1 subschema matches out of 2, #: #: 0 subschemas matched instead of one"}: the
     * count of a branch that failed, and no hint of <em>which property</em> failed or <em>why</em>.
     * <p>
     * Walking to the leaves instead yields {@code "#/give/qty: expected maximum: 3, found: 9"} —
     * the pointer, the keyword's own message, and nothing else. A caller (a client, or an agent
     * composing a request through the MCP server) can correct the document from that in one step.
     * <p>
     * A failed {@code oneOf}/{@code anyOf} is reported per branch rather than flattened, because
     * the branches are alternatives: flattening asks for the union of every branch's required keys,
     * which no valid document has. Each branch is labelled with its subschema {@code title} when
     * the schema author set one, so the reader can tell which alternative they were aiming at.
     * <p>
     * <b>A copy of this walk lives in {@code BodyValidator} in restheart-ai</b>, which reports the
     * identical failure for a body an agent composed through the MCP server. The two are duplicated
     * rather than shared because the walk needs everit and restheart-commons does not depend on it,
     * so the shared home would push that dependency onto every module — and restheart-ai does not
     * depend on restheart-mongodb either. Change one and change the other: the same schema and the
     * same document must produce the same message whichever way the write arrived, and nothing in
     * the build will tell you otherwise.
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

    @Override
    public String get(String schemaStoreDb, BsonValue schemaId)
            throws JsonSchemaNotFoundException {
        try {
            var raw = cache().getRaw(schemaStoreDb, schemaId);
            return raw.toJson();
        } catch (org.restheart.mongodb.handlers.schema.JsonSchemaNotFoundException ex) {
            throw new JsonSchemaNotFoundException(ex.getMessage(), ex);
        }
    }
}
