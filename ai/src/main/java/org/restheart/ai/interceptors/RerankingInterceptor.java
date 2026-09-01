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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.ai.util.PluginModelResolver;
import org.restheart.ai.util.RequestOverrides;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.utils.VarsInterpolator;
import org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.ai.RankedResult;
import org.restheart.plugins.ai.RerankModel;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * After an aggregation pipeline is executed, this interceptor optionally
 * re-ranks the results using the Atlas Reranking API when the aggregation
 * metadata declares a {@code rerank} attribute.
 *
 * <h2>Aggregation metadata example</h2>
 * <pre>{@code
 * PATCH /mydb/mycollection
 * {
 *   "aggrs": [
 *     {
 *       "type": "pipeline",
 *       "uri":  "vector_search",
 *       "stages": [
 *         { "_$vectorSearch": { "index": "myIdx", "path": "embedding",
 *                               "queryString": {"$var": "q"},
 *                               "numCandidates": 100, "limit": 20 } }
 *       ],
 *       "rerank": {
 *         "model": "voyage-rerank-2",
 *         "query": {"$var": "q"},
 *         "topK":  10
 *       }
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h2>Plugin configuration</h2>
 * <pre>{@code
 * rerankingInterceptor:
 *   enabled: false
 *   atlas-api-key:  <key>
 *   rerank-api-url: https://api.atlas.mongodb.com/api/v1/vectorSearch/rerank
 *   rerank-provider: cohereRerankProvider   # optional; see "Pluggable rerank provider" below
 * }</pre>
 *
 * <p>The {@code query} field in the {@code rerank} block accepts a literal string, or
 * the same {@code {"$var": "name"}} / {@code {"$var": ["name", default]}} syntax used
 * everywhere else in an aggregation definition, resolved against the current
 * aggregation variables.
 *
 * <h2>Pluggable rerank provider (Phase 2)</h2>
 * <p>When {@code rerank-provider} names a configured {@code Provider<RerankModel>}
 * (e.g. {@code cohereRerankProvider}, {@code voyageRerankProvider}), that provider is
 * used instead of the Atlas Reranking API. When unset (no static config, no
 * {@link RequestOverrides#RERANK_PROVIDER} override), behavior is unchanged from
 * Phase 1: the Atlas API is called directly via {@code atlas-api-key}/{@code rerank-api-url}.
 * The {@code rerank} metadata block's {@code model}/{@code query}/{@code topK} fields are
 * used identically in both cases — only which backend executes the rerank changes.
 *
 * <h2>Multi-tenant</h2>
 * <p>Per request, a deployment's tenant-config interceptor may attach
 * {@link RequestOverrides#ATLAS_API_KEY} / {@link RequestOverrides#RERANK_API_URL} /
 * {@link RequestOverrides#RERANK_PROVIDER} to use different values for that tenant.
 */
@RegisterPlugin(
    name = "rerankingInterceptor",
    description = "Re-ranks $vectorSearch aggregation results using the Atlas Reranking API",
    interceptPoint = InterceptPoint.RESPONSE,
    requiresContent = true,
    enabledByDefault = false
)
public class RerankingInterceptor implements MongoInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RerankingInterceptor.class);

    static final String RERANK_ELEMENT_NAME      = "rerank";
    static final String AGGREGATIONS_ELEMENT_NAME = "aggrs";

    // static, single-tenant defaults; overridden per request via RequestOverrides
    private String defaultAtlasApiKey;
    private String defaultRerankApiUrl;
    private String defaultRerankProviderName;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Inject("config")
    private Map<String, Object> config;

    @Inject("registry")
    private PluginsRegistry registry;

    // resolved lazily (once all plugins have finished @OnInit) and cached per provider
    // name, since override-ai-rerank-provider can name a different provider per request
    private final Map<String, RerankModel> resolvedRerankModels = new ConcurrentHashMap<>();

    @OnInit
    public void setup() {
        this.defaultAtlasApiKey  = argOrDefault(config, "atlas-api-key", "");
        this.defaultRerankApiUrl = argOrDefault(config, "rerank-api-url",
            "https://api.atlas.mongodb.com/api/v1/vectorSearch/rerank");
        this.defaultRerankProviderName = argOrDefault(config, "rerank-provider", "");
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        if (!request.isAggregation() || !request.isGet() || response.isInError()) {
            return false;
        }
        return findRerankConfig(request) != null;
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var rerankConfig = findRerankConfig(request);
        if (rerankConfig == null) return;

        var content = response.getContent();
        if (content == null || !content.isArray() || content.asArray().isEmpty()) return;

        var modelName = rerankConfig.getString("model", new BsonString("voyage-rerank-2")).getValue();
        var topK  = rerankConfig.getInt32("topK", new BsonInt32(content.asArray().size())).getValue();
        var query = resolveQuery(rerankConfig, request);

        if (query == null || query.isBlank()) {
            LOGGER.warn("rerankingInterceptor: cannot rerank – no query resolved for aggregation '{}'",
                request.getAggregationOperation());
            return;
        }

        var documents = extractTexts(content.asArray());
        if (documents.isEmpty()) return;

        var providerName = RequestOverrides.str(request, RequestOverrides.RERANK_PROVIDER, defaultRerankProviderName);

        BsonArray reranked;
        try {
            if (providerName.isBlank()) {
                // Phase 1 behavior: call the Atlas Reranking API directly.
                var atlasApiKey = RequestOverrides.str(request, RequestOverrides.ATLAS_API_KEY, defaultAtlasApiKey);
                var rerankApiUrl = RequestOverrides.str(request, RequestOverrides.RERANK_API_URL, defaultRerankApiUrl);
                reranked = callRerankApi(modelName, query, topK, content.asArray(), documents, atlasApiKey, rerankApiUrl);
            } else {
                // Phase 2: dispatch to the configured Provider<RerankModel>.
                var rerankModel = resolveRerankModel(providerName);
                if (rerankModel == null) {
                    return;
                }
                var ranked = rerankModel.rerank(query, documents, topK, request);
                reranked = applyRankedResults(content.asArray(), ranked);
            }
        } catch (Exception e) {
            LOGGER.error("rerankingInterceptor: rerank call failed: {}", e.getMessage(), e);
            response.addWarning("reranking failed: " + e.getMessage());
            return;
        }

        response.setContent(reranked);
        response.setCount(reranked.size());
    }

    /**
     * Resolved lazily (rather than in {@link #setup()}) so that this interceptor does
     * not depend on plugin initialization order relative to the configured provider.
     * Cached per provider name because {@link RequestOverrides#RERANK_PROVIDER} can
     * name a different provider on a per-request basis.
     */
    private RerankModel resolveRerankModel(String providerName) {
        var model = PluginModelResolver.resolve(registry, resolvedRerankModels, providerName, RerankModel.class);
        if (model.isEmpty()) {
            LOGGER.warn("rerankingInterceptor: rerank provider '{}' not found, not enabled, "
                + "or does not supply a RerankModel", providerName);
        }
        return model.orElse(null);
    }

    // -------------------------------------------------------------------------

    static BsonDocument findRerankConfig(MongoRequest request) {
        var collProps = request.getCollectionProps();
        if (collProps == null) return null;

        var _aggrs = collProps.get(AGGREGATIONS_ELEMENT_NAME);
        if (_aggrs == null || !_aggrs.isArray()) return null;

        var uri = request.getAggregationOperation();
        for (var item : _aggrs.asArray()) {
            if (!item.isDocument()) continue;
            var agg = item.asDocument();
            if (!uri.equals(agg.getString("uri", new BsonString("")).getValue())) continue;
            var rerank = agg.get(RERANK_ELEMENT_NAME);
            if (rerank != null && rerank.isDocument()) {
                // request.getCollectionProps() returns the raw stored metadata, where
                // $-prefixed keys (e.g. a "query": {"$var": "q"} inside this block) are
                // escaped as _$xxx (MongoDB disallows storing keys starting with $) --
                // unescape before resolveQuery() ever looks for the "$var" key, same fix
                // as VectorScanInterceptor.findStagesArray.
                return BsonUtils.unescapeKeys(rerank).asDocument();
            }
        }
        return null;
    }

    /**
     * Resolves the {@code query} field of a {@code rerank} block: a literal string is
     * used as-is, and {@code {"$var": "name"}} / {@code {"$var": ["name", default]}} are
     * resolved against the current request's aggregation variables — the same
     * {@code $var} syntax used everywhere else in an aggregation definition (stages,
     * {@code $vectorize}), so a query doesn't need its own bespoke reference syntax.
     * An unbound variable (no default given) is treated the same as no query at all:
     * {@code handle()} skips reranking and leaves the original results untouched.
     */
    static String resolveQuery(BsonDocument rerankConfig, MongoRequest request) {
        var queryVal = rerankConfig.get("query");
        if (queryVal == null) return null;

        BsonValue resolved;
        try {
            resolved = VarsInterpolator.interpolate(VAR_OPERATOR.$var, queryVal, request.getAggregationVars(), request);
        } catch (Exception e) {
            return null;
        }

        return resolved != null && resolved.isString() ? resolved.asString().getValue() : null;
    }

    private List<String> extractTexts(BsonArray results) {
        var texts = new ArrayList<String>(results.size());
        for (var item : results) {
            if (!item.isDocument()) { texts.add(""); continue; }
            var doc = item.asDocument();
            BsonValue tv = doc.get("text");
            texts.add(tv != null && tv.isString() ? tv.asString().getValue() : doc.toJson());
        }
        return texts;
    }

    private BsonArray callRerankApi(
        String model, String query, int topK,
        BsonArray originalResults, List<String> documents,
        String atlasApiKey, String rerankApiUrl) throws Exception {

        var docsJson = new StringBuilder("[");
        for (int i = 0; i < documents.size(); i++) {
            docsJson.append("\"").append(escape(documents.get(i))).append("\"");
            if (i < documents.size() - 1) docsJson.append(",");
        }
        docsJson.append("]");

        var payload = "{\"model\":\"" + escape(model) + "\""
            + ",\"query\":\"" + escape(query) + "\""
            + ",\"topK\":" + topK
            + ",\"documents\":" + docsJson + "}";

        var httpReq = HttpRequest.newBuilder()
            .uri(URI.create(rerankApiUrl))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + atlasApiKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        var httpResp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (httpResp.statusCode() != 200) {
            throw new RuntimeException("Rerank API returned HTTP " + httpResp.statusCode()
                + ": " + httpResp.body());
        }

        return applyRanking(originalResults, httpResp.body());
    }

    /**
     * Reorders {@code originalResults} according to the rerank API's response body
     * ({@code [{"index": N, "score": F}, ...]}), appending {@code _rerankScore} to each
     * surviving document. Entries with an out-of-range or missing {@code index}, or that
     * don't reference a document, are skipped. Pulled out of {@link #callRerankApi} so
     * the response-parsing logic can be unit-tested without an HTTP round-trip.
     */
    static BsonArray applyRanking(BsonArray originalResults, String rerankResponseBody) {
        var respDoc  = BsonDocument.parse("{\"results\":" + rerankResponseBody + "}");
        var rankings = respDoc.getArray("results");

        var reranked = new BsonArray();
        for (var r : rankings) {
            if (!r.isDocument()) continue;
            var idx = r.asDocument().getInt32("index", new BsonInt32(-1)).getValue();
            if (idx < 0 || idx >= originalResults.size()) continue;
            var doc = originalResults.get(idx);
            if (doc.isDocument()) {
                var enriched = doc.asDocument().clone();
                enriched.append("_rerankScore",
                    r.asDocument().getOrDefault("score", new BsonDouble(0)));
                reranked.add(enriched);
            }
        }
        return reranked;
    }

    /**
     * Reorders {@code originalResults} according to already-parsed {@link RankedResult}s
     * from a {@code Provider<RerankModel>} (Phase 2 path — see {@link #applyRanking} for
     * the Phase 1/Atlas-response-body equivalent). Same skip semantics as
     * {@code applyRanking}: an out-of-range index, or an index that doesn't reference a
     * document, is skipped rather than failing the whole rerank.
     */
    static BsonArray applyRankedResults(BsonArray originalResults, List<RankedResult> ranked) {
        var reranked = new BsonArray();
        for (var r : ranked) {
            if (r.index() < 0 || r.index() >= originalResults.size()) continue;
            var doc = originalResults.get(r.index());
            if (doc.isDocument()) {
                var enriched = doc.asDocument().clone();
                enriched.append("_rerankScore", new BsonDouble(r.score()));
                reranked.add(enriched);
            }
        }
        return reranked;
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
