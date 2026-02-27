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
import java.util.Map;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
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
 *                               "queryVector": {"$var": "qv"},
 *                               "numCandidates": 100, "limit": 20 } }
 *       ],
 *       "rerank": {
 *         "model": "voyage-rerank-2",
 *         "query": "$avars.q",
 *         "topK":  10
 *       }
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h2>Plugin configuration (plugins-args)</h2>
 * <pre>{@code
 * plugins-args:
 *   rerankingInterceptor:
 *     enabled: false
 *     atlas-api-key:  <key>
 *     rerank-api-url: https://api.atlas.mongodb.com/api/v1/vectorSearch/rerank
 * }</pre>
 *
 * <p>The {@code query} field in the {@code rerank} block supports a simple
 * {@code "$avars.<varName>"} expression resolved against the current aggregation
 * variables.
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

    private String atlasApiKey;
    private String rerankApiUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void setup() {
        this.atlasApiKey  = argOrDefault(config, "atlas-api-key", "");
        this.rerankApiUrl = argOrDefault(config, "rerank-api-url",
            "https://api.atlas.mongodb.com/api/v1/vectorSearch/rerank");
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

        var model = rerankConfig.getString("model", new BsonString("voyage-rerank-2")).getValue();
        var topK  = rerankConfig.getInt32("topK", new BsonInt32(content.asArray().size())).getValue();
        var query = resolveQuery(rerankConfig, request);

        if (query == null || query.isBlank()) {
            LOGGER.warn("rerankingInterceptor: cannot rerank – no query resolved for aggregation '{}'",
                request.getAggregationOperation());
            return;
        }

        var documents = extractTexts(content.asArray());
        if (documents.isEmpty()) return;

        BsonArray reranked;
        try {
            reranked = callRerankApi(model, query, topK, content.asArray(), documents);
        } catch (Exception e) {
            LOGGER.error("rerankingInterceptor: rerank API call failed: {}", e.getMessage(), e);
            response.addWarning("reranking failed: " + e.getMessage());
            return;
        }

        response.setContent(reranked);
        response.setCount(reranked.size());
    }

    // -------------------------------------------------------------------------

    private BsonDocument findRerankConfig(MongoRequest request) {
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
            if (rerank != null && rerank.isDocument()) return rerank.asDocument();
        }
        return null;
    }

    private String resolveQuery(BsonDocument rerankConfig, MongoRequest request) {
        var queryVal = rerankConfig.get("query");
        if (queryVal == null || !queryVal.isString()) return null;

        var raw = queryVal.asString().getValue();
        if (raw.startsWith("$avars.")) {
            var varName = raw.substring("$avars.".length());
            var avars = request.getAggregationVars();
            if (avars != null) {
                var v = avars.get(varName);
                if (v != null && v.isString()) return v.asString().getValue();
            }
            return null;
        }
        return raw;
    }

    private java.util.List<String> extractTexts(BsonArray results) {
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
        BsonArray originalResults, java.util.List<String> documents) throws Exception {

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

        // Response: [{"index": N, "score": F}, ...]
        var respDoc  = BsonDocument.parse("{\"results\":" + httpResp.body() + "}");
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

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
