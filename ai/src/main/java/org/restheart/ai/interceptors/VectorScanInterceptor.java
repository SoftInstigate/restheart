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
package org.restheart.ai.interceptors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.restheart.ai.util.RequestOverrides;
import org.restheart.ai.vectorscan.VectorSimilarity;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.utils.StagesInterpolator;
import org.restheart.mongodb.utils.StagesInterpolator.STAGE_OPERATOR;
import org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

import io.undertow.util.Headers;

/**
 * Executes {@code $vectorScan}: brute-force (exact, unindexed) vector similarity
 * search. Unlike {@code $vectorSearch}, it needs no mongot, no Atlas, and no vector
 * search index of any kind — it computes distances directly against a plain array
 * field. The trade-off is scale: it scores every candidate document one by one, so it
 * fits small-to-medium collections; {@code $vectorSearch} remains the answer once a
 * collection needs to scale past what a full scan can do. See restheart/#712.
 *
 * <h2>Pipeline stage</h2>
 * <pre>{@code
 * PATCH /mydb/articles
 * { "aggrs": [{ "uri": "semantic-search", "type": "pipeline", "stages": [
 *     { "$match": { "status": "published" } },
 *     { "$sort": { "priority": -1 } },
 *     { "$vectorScan": {
 *         "path": "embedding",
 *         "queryVector": { "$vectorize": { "$var": "q" } },
 *         "similarity": "cosine",
 *         "maxCandidates": 10000,
 *         "limit": 10
 *     }}
 * ]}]}
 * }</pre>
 *
 * <ul>
 *   <li>{@code path} (required) — the document field holding the stored vector.</li>
 *   <li>{@code queryVector} (required) — an array of numbers, or resolved via
 *       {@code $vectorize}/{@code $var} like any other stage value.</li>
 *   <li>{@code similarity} — {@code cosine} (default) | {@code dotProduct} |
 *       {@code euclidean}, see {@link VectorSimilarity}.</li>
 *   <li>{@code maxCandidates} — caps how many documents get scored (default
 *       configurable via {@code default-max-candidates}, itself defaulting to 10000).
 *       Enforced by injecting a real {@code $limit} stage right before scanning —
 *       <strong>not</strong> a substitute for filtering: without a preceding
 *       {@code $sort}, "the first {@code maxCandidates}" is whatever order MongoDB
 *       happens to return, not a deliberate sample.</li>
 *   <li>{@code limit} — top-K results returned (default configurable via
 *       {@code default-limit}, itself defaulting to 10).</li>
 * </ul>
 *
 * <h2>Caps the stage cannot exceed</h2>
 * <p>The stage belongs to the collection owner; the node belongs to its operator. Scoring
 * happens here, not in MongoDB, so on a shared node one aggregation asking for
 * {@code maxCandidates: 500000} pulls half a million vectors into memory that every
 * tenant shares. {@code max-candidates-cap} and {@code max-limit-cap} bound what any
 * stage may ask for: the effective value is {@code min(requested, cap)}, where
 * {@code requested} is the stage's value or the default. Unset (the default) means no
 * cap, so nothing changes for existing deployments. A multi-tenant deployment attaches
 * {@link RequestOverrides#MAX_CANDIDATES_CAP} and {@link RequestOverrides#MAX_LIMIT_CAP}
 * per request to make the cap a property of the plan; an attached value wins over the
 * configured one, and {@code 0} lifts it. See restheart/#755.
 *
 * <p>A capped request is told so: the response carries a {@code Warning: 299} header
 * naming the requested and the effective value (the body of an aggregation is a plain
 * array, so there is no {@code _warnings} field to put it in), the same text is added to
 * the response's warnings for the representations that render them, and it is logged at
 * {@code debug}. An owner comparing results with and without the cap should not have to
 * guess why they differ.
 *
 * <p>One {@code $vectorScan} per pipeline: the stages after it are bridged back to MongoDB,
 * which does not know the stage, so a second one is refused with a {@code 400} up front
 * rather than failing there with an error that names nothing.
 *
 * <h2>Filtering: real MongoDB stages, not a restricted sub-object</h2>
 * <p>Unlike {@code $vectorSearch}'s own {@code filter} (a limited operator subset),
 * anything placed <em>before</em> {@code $vectorScan} in the pipeline — {@code $match},
 * {@code $sort}, {@code $lookup}, anything — runs natively on MongoDB with the full
 * query language. This is what makes brute force viable on large collections: filter
 * hard first, scan only the survivors.
 *
 * <h2>Execution</h2>
 * <p>{@code $vectorScan} never reaches the MongoDB driver — this interceptor finds it
 * in the (already {@code $var}/{@code $ifvar}-interpolated) stored pipeline, at
 * position {@code i}, and:
 * <ol>
 *   <li>Runs {@code stages[0..i-1] + [{$limit: maxCandidates}]} as a real aggregation —
 *       the injected {@code $limit} is what actually enforces {@code maxCandidates}.</li>
 *   <li>Scores each candidate in Java, sorts, truncates to {@code limit}, and attaches
 *       a plain {@code score} field to each result (see {@link VectorSimilarity} for
 *       its "higher is always better" convention).</li>
 *   <li>If stages exist after {@code $vectorScan}, bridges them back into MongoDB with
 *       a second, database-level aggregate: {@code [{$documents: <winners>},
 *       ...stages[i+1..end]]} ({@code $documents}, MongoDB 6.0+, runs a pipeline
 *       against a literal array instead of a collection). Otherwise the winners are
 *       the response directly.</li>
 *   <li>Attaches the param {@link MongoRequest#AGGREGATION_ANSWERED} to the request after
 *       building the response, so that {@code GetAggregationHandler} leaves it as it is
 *       (it would otherwise send the unresolved {@code $vectorScan} stage to MongoDB and get
 *       a real error back). The request is not marked in error: that would skip the
 *       {@code RESPONSE} interceptors too, and {@code rerankingInterceptor} and the
 *       representation must run on the result as on any other aggregation.</li>
 * </ol>
 *
 * <h2>Known limitation</h2>
 * <p>This bypasses {@code GetAggregationHandler}, so it does not run the standard
 * aggregation-security (blacklisted stages/operators) check that normal pipelines go
 * through. Deployments relying on that check should be aware {@code $vectorScan}
 * pipelines are not currently covered by it.
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * vectorScanInterceptor:
 *   enabled: false                 # must be explicitly enabled
 *   default-max-candidates: 10000  # used when a stage omits maxCandidates
 *   default-limit: 10              # used when a stage omits limit
 *   # max-candidates-cap: 2000     # no stage may score more than this; unset = no cap
 *   # max-limit-cap: 100           # no stage may return more than this; unset = no cap
 * }</pre>
 */
@RegisterPlugin(
        name = "vectorScanInterceptor",
        description = "Executes $vectorScan: brute-force vector similarity search requiring no mongot or index",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
        priority = Integer.MIN_VALUE,
        enabledByDefault = false
)
public class VectorScanInterceptor implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(VectorScanInterceptor.class);

    private static final String STAGE_NAME = "$vectorScan";
    private static final String AGGREGATIONS_ELEMENT_NAME = "aggrs";

    @Inject("config")
    private Map<String, Object> config;

    @Inject("mclient")
    private MongoClient mclient;

    private int defaultMaxCandidates;
    private int defaultLimit;
    // 0 means no cap; see class javadoc, "Caps the stage cannot exceed"
    private int maxCandidatesCap;
    private int maxLimitCap;

    @OnInit
    public void setup() {
        this.defaultMaxCandidates = argOrDefault(config, "default-max-candidates", 10000);
        this.defaultLimit = argOrDefault(config, "default-limit", 10);
        this.maxCandidatesCap = capOrNone(config, "max-candidates-cap");
        this.maxLimitCap = capOrNone(config, "max-limit-cap");
    }

    /**
     * A cap from the configuration: {@code 0} (no cap) when the key is absent, blank or not a
     * positive number, so a commented-out or emptied key behaves like a missing one.
     */
    static int capOrNone(Map<String, Object> config, String key) {
        var v = config == null ? null : config.get(key);
        return v instanceof Number n && n.intValue() > 0 ? n.intValue() : 0;
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        if (!request.isAggregation() || !request.isGet() || response.isInError()) {
            return false;
        }
        var stages = findStagesArray(request);
        return stages != null && containsVectorScanStage(stages);
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var dbName = request.getDBName();
        var collName = request.getCollectionName();

        var rawStages = findStagesArray(request);
        if (rawStages == null) {
            // resolve() already checked this; defensive only
            return;
        }

        List<BsonDocument> stages;
        try {
            var avars = request.getAggregationVars() == null ? new BsonDocument() : request.getAggregationVars();
            StagesInterpolator.injectAvars(request, avars);
            stages = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, rawStages, avars, request);
        } catch (Exception e) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "cannot resolve $vectorScan pipeline: " + e.getMessage());
            return;
        }

        var scanIndex = indexOfVectorScanStage(stages);
        if (scanIndex < 0) {
            // e.g. $vectorScan was itself wrapped in an unbound $ifvar and interpolated
            // away — nothing to scan; let the (now vectorScan-free) pipeline run normally
            LOGGER.debug("vectorScanInterceptor: no $vectorScan stage after interpolation for {}/{}, letting standard handling proceed", dbName, collName);
            return;
        }

        var second = secondVectorScanStage(stages, scanIndex);
        if (second >= 0) {
            // the stages after the first are bridged back to MongoDB, which does not know this one
            response.setInError(HttpStatus.SC_BAD_REQUEST, "$vectorScan: one per pipeline; a second one is at stage " + second);
            return;
        }

        var scanConfig = stages.get(scanIndex).get(STAGE_NAME);
        if (scanConfig == null || !scanConfig.isDocument()) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "$vectorScan must be a document");
            return;
        }
        var scanArgs = scanConfig.asDocument();

        var pathValue = scanArgs.get("path");
        if (pathValue == null || !pathValue.isString()) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "$vectorScan requires a string 'path'");
            return;
        }
        var path = pathValue.asString().getValue();

        var queryVectorValue = scanArgs.get("queryVector");
        float[] queryVector;
        if (queryVectorValue == null || !queryVectorValue.isArray()) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "$vectorScan requires an array 'queryVector'");
            return;
        }
        try {
            queryVector = toFloatArray(queryVectorValue.asArray());
        } catch (Exception e) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "$vectorScan 'queryVector' must be an array of numbers");
            return;
        }

        var similarity = scanArgs.containsKey("similarity") && scanArgs.get("similarity").isString()
                ? scanArgs.getString("similarity").getValue()
                : VectorSimilarity.COSINE;

        var requestedMaxCandidates = scanArgs.containsKey("maxCandidates") && scanArgs.get("maxCandidates").isNumber()
                ? scanArgs.get("maxCandidates").asNumber().intValue()
                : defaultMaxCandidates;

        var requestedLimit = scanArgs.containsKey("limit") && scanArgs.get("limit").isNumber()
                ? scanArgs.get("limit").asNumber().intValue()
                : defaultLimit;

        // the caps are the operator's, per node or per request (plan), and the stage cannot exceed them
        var maxCandidates = bound(request, response, "maxCandidates", requestedMaxCandidates,
                RequestOverrides.intVal(request, RequestOverrides.MAX_CANDIDATES_CAP, maxCandidatesCap));
        var limit = bound(request, response, "limit", requestedLimit,
                RequestOverrides.intVal(request, RequestOverrides.MAX_LIMIT_CAP, maxLimitCap));

        var beforeStages = new ArrayList<>(stages.subList(0, scanIndex));
        beforeStages.add(new BsonDocument("$limit", new BsonInt32(maxCandidates)));

        List<BsonDocument> candidates;
        try {
            candidates = mclient.getDatabase(dbName)
                    .getCollection(collName, BsonDocument.class)
                    .aggregate(beforeStages)
                    .into(new ArrayList<>());
        } catch (Exception e) {
            LOGGER.error("vectorScanInterceptor: candidate fetch failed for {}/{}: {}", dbName, collName, e.getMessage(), e);
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "$vectorScan candidate fetch failed: " + e.getMessage());
            return;
        }

        var winningDocs = scoreAndRank(candidates, path, queryVector, similarity, limit);

        BsonArray finalResult;
        if (scanIndex + 1 < stages.size()) {
            var afterPipeline = new ArrayList<BsonDocument>();
            afterPipeline.add(new BsonDocument("$documents", winningDocs));
            afterPipeline.addAll(stages.subList(scanIndex + 1, stages.size()));
            try {
                var afterResult = mclient.getDatabase(dbName)
                        .aggregate(afterPipeline, BsonDocument.class)
                        .into(new ArrayList<BsonDocument>());
                finalResult = new BsonArray();
                afterResult.forEach(finalResult::add);
            } catch (Exception e) {
                LOGGER.error("vectorScanInterceptor: post-scan pipeline failed for {}/{}: {}", dbName, collName, e.getMessage(), e);
                response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "$vectorScan post-scan pipeline failed: " + e.getMessage());
                return;
            }
        } else {
            finalResult = winningDocs;
        }

        response.setContent(finalResult);
        response.setCount(finalResult.size());
        response.setContentTypeAsJson();
        response.setStatusCode(HttpStatus.SC_OK);

        // the result is written: GetAggregationHandler must leave it as it is -- see class javadoc
        request.attachParam(MongoRequest.AGGREGATION_ANSWERED, true);
    }

    // -------------------------------------------------------------------------

    /**
     * The value the scan runs with: {@code requested} unless a cap is set and it is above it.
     * When it is, the response says so in a {@code Warning: 299} header and in its warnings,
     * and the debug log records it.
     */
    private static int bound(MongoRequest request, MongoResponse response, String option, int requested, int cap) {
        var effective = capped(requested, cap);
        if (effective != requested) {
            var message = cappedMessage(option, requested, effective);
            response.addWarning(message);
            response.getHeaders().add(Headers.WARNING, "299 - \"" + message + "\"");
            LOGGER.debug("vectorScanInterceptor: {} for {}/{}", message, request.getDBName(), request.getCollectionName());
        }
        return effective;
    }

    /** {@code min(requested, cap)}; a cap of zero or less is no cap. */
    static int capped(int requested, int cap) {
        return cap > 0 && requested > cap ? cap : requested;
    }

    static String cappedMessage(String option, int requested, int effective) {
        return "$vectorScan " + option + " " + requested + " exceeds the cap of this deployment, capped to " + effective;
    }

    static BsonArray scoreAndRank(List<BsonDocument> candidates, String path, float[] queryVector, String similarity, int limit) {
        record Scored(BsonDocument doc, double score) {
        }

        var scored = new ArrayList<Scored>(candidates.size());
        for (var doc : candidates) {
            var vectorValue = doc.get(path);
            if (vectorValue == null || !vectorValue.isArray()) {
                continue;
            }
            float[] docVector;
            try {
                docVector = toFloatArray(vectorValue.asArray());
            } catch (Exception e) {
                continue;
            }
            if (docVector.length != queryVector.length) {
                continue;
            }
            scored.add(new Scored(doc, VectorSimilarity.score(similarity, queryVector, docVector)));
        }

        scored.sort((a, b) -> Double.compare(b.score(), a.score()));

        var winners = scored.size() > limit ? scored.subList(0, limit) : scored;

        var result = new BsonArray();
        for (var w : winners) {
            var d = w.doc().clone();
            d.append("score", new BsonDouble(w.score()));
            result.add(d);
        }
        return result;
    }

    static float[] toFloatArray(BsonArray array) {
        var result = new float[array.size()];
        for (int i = 0;i < array.size();i++) {
            result[i] = (float) array.get(i).asNumber().doubleValue();
        }
        return result;
    }

    static BsonArray findStagesArray(MongoRequest request) {
        var collProps = request.getCollectionProps();
        if (collProps == null) {
            return null;
        }
        var aggrs = collProps.get(AGGREGATIONS_ELEMENT_NAME);
        if (aggrs == null || !aggrs.isArray()) {
            return null;
        }
        var uri = request.getAggregationOperation();
        for (var item : aggrs.asArray()) {
            if (!item.isDocument()) {
                continue;
            }
            var doc = item.asDocument();
            var uriValue = doc.get("uri");
            if (uriValue != null && uriValue.isString() && uriValue.asString().getValue().equals(uri)) {
                var stages = doc.get("stages");
                // request.getCollectionProps() returns the raw stored metadata, where
                // $-prefixed keys are escaped as _$xxx (MongoDB disallows storing keys
                // starting with $) -- StagesInterpolator.interpolate() unescapes this
                // internally as its first step, but that happens later, in handle();
                // this lookup (also used by resolve(), before interpolation ever runs)
                // must unescape itself to compare against the literal "$vectorScan" key.
                return stages != null && stages.isArray() ? BsonUtils.unescapeKeys(stages).asArray() : null;
            }
        }
        return null;
    }

    static boolean containsVectorScanStage(BsonArray stages) {
        return indexOfVectorScanStage(stages) >= 0;
    }

    static int indexOfVectorScanStage(List<BsonDocument> stages) {
        for (int i = 0;i < stages.size();i++) {
            var s = stages.get(i);
            if (s.size() == 1 && STAGE_NAME.equals(s.keySet().iterator().next())) {
                return i;
            }
        }
        return -1;
    }

    /** The index of a {@code $vectorScan} after the one at {@code first}, or -1: a pipeline gets one. */
    static int secondVectorScanStage(List<BsonDocument> stages, int first) {
        var after = indexOfVectorScanStage(stages.subList(first + 1, stages.size()));
        return after < 0 ? -1 : first + 1 + after;
    }

    static int indexOfVectorScanStage(BsonArray stages) {
        for (int i = 0;i < stages.size();i++) {
            var item = stages.get(i);
            if (item.isDocument()) {
                var s = item.asDocument();
                if (s.size() == 1 && STAGE_NAME.equals(s.keySet().iterator().next())) {
                    return i;
                }
            }
        }
        return -1;
    }
}
