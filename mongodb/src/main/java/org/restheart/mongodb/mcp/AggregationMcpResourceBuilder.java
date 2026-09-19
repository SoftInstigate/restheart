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
package org.restheart.mongodb.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.plugins.mcp.BsonJava;
import org.restheart.exchange.ExchangeKeys;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.security.AggregationPipelineSecurityChecker;

/**
 * Builds the {@code McpResource} for one aggregation pipeline declared in a collection's
 * {@code aggrs} metadata, honoring its own {@code mcp} block (see #616): {@code enabled},
 * {@code description} (required — an aggregation with no description is not surfaced),
 * {@code params} (per-variable overrides for {@code $var} references, keyed by variable name),
 * {@code examples}, and {@code pipeline_summary} — an operator-supplied override for the
 * otherwise auto-generated {@link PipelineSummarizer} heuristic, which can't meaningfully
 * describe non-linear pipelines ({@code $lookup}/{@code $facet}/...).
 *
 * <p>Each declared variable surfaces as a param of its own. Since 9.9 RESTHeart binds any query
 * parameter it does not reserve for itself as a {@code $var}, so {@code ?status=A} is all a caller
 * needs, and an agent filling one field per variable has an easier job than one assembling a JSON
 * blob. A variable whose name collides with a reserved parameter ({@code page}, {@code sort},
 * {@code filter}, ...) cannot travel that way and keeps the legacy form, bundled into
 * {@code avars} — which still works, for every variable, against any version.
 */
public final class AggregationMcpResourceBuilder {

    private AggregationMcpResourceBuilder() {
    }

    /**
     * @param collectionUri  the owning collection's resource URI (e.g. {@code https://host/db/coll})
     * @param aggrUri        the aggregation's own {@code uri} ({@code aggrs[].uri})
     * @param stages         the aggregation's pipeline ({@code aggrs[].stages})
     * @param mcp            the aggregation entry's own {@code mcp} block, or {@code null} if absent
     * @param dbName         the owning database's name, for {@code securityChecker}'s cross-database checks
     * @param securityChecker the same {@link AggregationPipelineSecurityChecker} the real
     *                       {@code GetAggregationHandler} enforces at execution time — used here
     *                       to decide the {@code execute} action's {@code readable} flag: a
     *                       pipeline the operator's own blacklist would reject (e.g. {@code $out},
     *                       {@code $merge} — both blocked by default) is never marked {@code
     *                       readable}, so {@code resources/read} can't trigger it. This is a
     *                       pre-check against the raw, un-interpolated pipeline; the real check
     *                       runs again on the bound pipeline at actual execution time.
     * @return the resource, or empty if not MCP-enabled: no {@code mcp} block,
     *         {@code mcp.enabled == false}, or a missing required {@code description}
     */
    public static Optional<McpResource> build(String collectionUri, String aggrUri, BsonValue stages, BsonDocument mcp,
                                              String dbName, AggregationPipelineSecurityChecker securityChecker) {
        if (mcp == null || isExplicitlyDisabled(mcp) || description(mcp) == null) {
            return Optional.empty();
        }

        var pathTemplate = "/_aggrs/" + aggrUri;
        var declaredParams = mcp.get("params") instanceof BsonDocument pd ? pd : new BsonDocument();
        var scanResult = PipelineParamScanner.scan(stages);
        var referencedNames = scanResult.names();
        var warnings = new ArrayList<String>();

        var builder = McpResource.builder()
                .uri(collectionUri + pathTemplate)
                .kind("aggregation")
                // Watched through the collection it reads from: MongoDB cannot say when a
                // pipeline's result changes, but a notification only means "re-read", and the
                // source collection changing is exactly when re-reading is worth it.
                .subscribable(true)
                .description(description(mcp));

        // one entry per variable, split by whether its name can travel as a query parameter
        var flatParams = new LinkedHashMap<String, McpResource.Param>();
        var reservedNameParams = new LinkedHashMap<String, McpResource.Param>();

        referencedNames.forEach(name -> {
            var requiredByShape = scanResult.isRequired(name);
            final McpResource.Param param;

            if (declaredParams.get(name) instanceof BsonDocument paramDef) {
                param = toParam(paramDef, requiredByShape);
            } else {
                param = new McpResource.Param("string", null, requiredByShape, null, null);
                warnings.add("$var '" + name + "' is not declared in mcp.params; defaulted to a "
                        + (requiredByShape ? "required" : "optional") + " string");
            }

            if (ExchangeKeys.RESERVED_QPARAM_KEYS.contains(name)) {
                reservedNameParams.put(name, param);
                warnings.add("$var '" + name + "' has the name of a reserved query parameter, so it can only be "
                        + "passed inside avars");
            } else {
                flatParams.put(name, param);
            }
        });

        declaredParams.keySet().stream()
                .filter(name -> !referencedNames.contains(name))
                .forEach(name -> warnings.add("mcp.params declares '" + name + "' but the pipeline does not reference it"));

        // one check, two uses: whether the read side is offered at all, and — when it is not —
        // what to tell an agent that would otherwise learn it only from a 403
        var refusal = pipelineRefusal(stages, dbName, securityChecker);

        builder.action("execute", a -> {
            a.method("GET");
            // the resource's own uri already IS this aggregation's full address (built just above
            // as collectionUri + pathTemplate) — how_to_call composes url = resource.uri() +
            // action.pathTemplate(), so the action's own path_template must be relative (empty),
            // not repeat pathTemplate, or the rendered URL doubles up "/_aggrs/<uri>"
            a.pathTemplate("");
            a.description(describeWithRefusal(description(mcp), refusal));
            a.readable(refusal == null);
            a.param("jsonMode", "string", false);

            // A pipeline that pages itself with @skip/@limit answers to ?page and ?pagesize —
            // RESTHeart computes those variables from them. Declared here because they are the
            // caller's half of that: without them an agent reads page one and has no way to learn
            // there is a second, and @skip itself is not something it can set.
            if (PipelineParamScanner.paginates(scanResult)) {
                a.param("page", new McpResource.Param("integer",
                        "Which page of results to return, starting at 1.", false, null, 1));
                a.param("pagesize", new McpResource.Param("integer",
                        "How many results per page.", false, null, null));
            }

            // Since 9.9 every non-reserved query parameter is bound as a $var, so each one is
            // declared on its own: how_to_call renders "?status=A", and an agent has a field to
            // fill rather than a JSON object to compose.
            flatParams.forEach(a::param);

            // The exceptions keep the legacy shape, because a parameter named `page` or `sort`
            // would be read by RESTHeart itself before the pipeline ever saw it.
            if (!reservedNameParams.isEmpty()) {
                var required = reservedNameParams.values().stream().anyMatch(McpResource.Param::required);
                a.param("avars", new McpResource.Param("object",
                        "MongoDB $var bindings whose names are reserved query parameters, so they cannot be sent flat.",
                        required, null, null, reservedNameParams));
            }
        });

        examples(mcp).forEach(ex -> builder.example(
                stringOrNull(ex, "description"),
                "execute",
                ex.get("args") instanceof BsonDocument args ? BsonJava.toMap(args) : Map.of()));

        builder.extra("pipeline_summary", pipelineSummary(mcp, stages));
        if (!warnings.isEmpty()) {
            builder.extra("warnings", warnings);
        }

        return Optional.of(builder.build());
    }

    /**
     * The raw pipeline, before any {@code $var} binding, checked against the operator's own
     * {@code aggregationSecurity} policy (stage/operator blacklists, cross-database restrictions —
     * default blacklist blocks {@code $out}/{@code $merge}, among others; if the operator disables
     * {@code aggregationSecurity} entirely, the checker itself treats everything as passing,
     * consistent with how the real {@code GetAggregationHandler} would then run it unchecked too).
     * A {@code null} checker (should not happen — {@link MongoMcpAwareImpl#create} always supplies
     * one) or a pipeline that isn't a well-formed stage array defaults to {@code false} — never
     * {@code readable} on anything we can't positively clear.
     */
    private static String pipelineRefusal(BsonValue stages, String dbName, AggregationPipelineSecurityChecker securityChecker) {
        if (securityChecker == null) {
            return "the deployment's aggregation security is unavailable";
        }
        if (!(stages instanceof BsonArray stagesArray)) {
            return "the pipeline is not a well-formed array of stages";
        }
        return securityChecker.validatePipeline(stagesArray, dbName)
                .map(v -> "the deployment's aggregation security refuses this pipeline (" + v.type() + " '" + v.operator() + "' at " + v.location() + ")")
                .orElse(null);
    }

    /**
     * An aggregation whose pipeline the security checker refuses cannot be read <em>or</em>
     * executed: the REST endpoint behind it answers {@code 403}, so both {@code resources/read}
     * and {@code call_api} fail. Saying so in the action's own description is the only place an
     * agent finds out before trying — the resource stays in the catalogue, because it is the
     * owner's own opt-in and hiding it would leave them wondering where it went, but it no longer
     * looks callable.
     */
    private static String describeWithRefusal(String description, String refusal) {
        if (refusal == null) {
            return description;
        }
        var note = "Not callable: " + refusal + ". Reading or invoking it answers 403.";
        return description == null || description.isBlank() ? note : description + " " + note;
    }

    private static boolean isExplicitlyDisabled(BsonDocument mcp) {
        var enabled = mcp.get("enabled");
        return enabled != null && enabled.isBoolean() && !enabled.asBoolean().getValue();
    }

    private static String description(BsonDocument mcp) {
        return stringOrNull(mcp, "description");
    }

    private static String pipelineSummary(BsonDocument mcp, BsonValue stages) {
        var override = stringOrNull(mcp, "pipeline_summary");
        return override != null ? override : PipelineSummarizer.summarize(stages);
    }

    private static List<BsonDocument> examples(BsonDocument mcp) {
        if (!(mcp.get("examples") instanceof BsonArray arr)) {
            return List.of();
        }
        return arr.stream().filter(BsonValue::isDocument).map(BsonValue::asDocument).toList();
    }

    private static String stringOrNull(BsonDocument doc, String key) {
        var v = doc.get(key);
        return v != null && v.isString() ? v.asString().getValue() : null;
    }

    /** @param defaultRequired required-ness derived from the pipeline's own shape (see {@link PipelineParamScanner}), used unless {@code def} explicitly overrides it with its own {@code required} field */
    private static McpResource.Param toParam(BsonDocument def, boolean defaultRequired) {
        var type = stringOrNull(def, "type");
        var description = stringOrNull(def, "description");
        var required = def.get("required") instanceof BsonBoolean b ? b.getValue() : defaultRequired;
        List<Object> enumValues = def.get("enum") instanceof BsonArray arr
                ? arr.stream().map(BsonJava::toJava).toList()
                : null;
        var defaultValue = def.get("default") != null ? BsonJava.toJava(def.get("default")) : null;
        return new McpResource.Param(type, description, required, enumValues, defaultValue);
    }
}
