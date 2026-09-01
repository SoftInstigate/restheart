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
package org.restheart.ai.rerank.providers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.restheart.ai.util.RequestOverrides;
import org.restheart.exchange.Request;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.ai.RankedResult;
import org.restheart.plugins.ai.RerankModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rerank provider for the Voyage AI Reranker API (verified against
 * https://docs.voyageai.com/reference/reranker-api on 2026-09-01):
 * {@code POST https://api.voyageai.com/v1/rerank}, Bearer auth, request
 * {@code {"model", "query", "documents", "top_k"}}, response
 * {@code {"object": "list", "data": [{"index": ..., "relevance_score": ...}]}} — same
 * per-result field names as Cohere ({@code index}, {@code relevance_score}), so
 * response parsing is shared via {@link RerankWireParsing}; only the top-level array
 * field ({@code data} vs Cohere's {@code results}) and the result-count request field
 * ({@code top_k} vs Cohere's {@code top_n}) differ.
 *
 * <pre>{@code
 * plugins-args:
 *   voyageRerankProvider:
 *     enabled: true
 *     api-key: <key>
 *     model: rerank-2.5        # optional, this is the default
 * }</pre>
 *
 * <h2>Multi-tenant</h2>
 * <p>Per request, a deployment's tenant-config interceptor may attach
 * {@link RequestOverrides#VOYAGE_RERANK_API_KEY}, {@link RequestOverrides#VOYAGE_RERANK_MODEL},
 * {@link RequestOverrides#VOYAGE_RERANK_BASE_URL} to use different values for that tenant —
 * kept distinct from {@code voyageEmbeddingProvider}'s own overrides since a tenant may
 * use a different Voyage key/model for reranking than for embeddings.
 */
@RegisterPlugin(
    name = "voyageRerankProvider",
    description = "Re-ranks documents against a query via the Voyage AI Reranker API",
    enabledByDefault = false
)
public class VoyageRerankProvider implements Provider<RerankModel> {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoyageRerankProvider.class);

    private static final String DEFAULT_BASE_URL = "https://api.voyageai.com/v1";
    private static final String DEFAULT_MODEL = "rerank-2.5";

    @Inject("config")
    private Map<String, Object> config;

    // static, single-tenant defaults; overridden per request via RequestOverrides
    private String defaultApiKey;
    private String defaultModel;
    private String defaultBaseUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private RerankModel instance;

    @OnInit
    public void init() {
        this.defaultApiKey = argOrDefault(config, "api-key", "");
        this.defaultModel = argOrDefault(config, "model", DEFAULT_MODEL);
        this.defaultBaseUrl = argOrDefault(config, "base-url", DEFAULT_BASE_URL);

        if (defaultApiKey == null || defaultApiKey.isBlank()) {
            LOGGER.warn("voyageRerankProvider: no api-key configured, rerank calls will fail "
                + "unless every request overrides it via {}", RequestOverrides.VOYAGE_RERANK_API_KEY);
        }

        this.instance = this::rerank;
    }

    @Override
    public RerankModel get(final PluginRecord<?> caller) {
        return instance;
    }

    private List<RankedResult> rerank(String query, List<String> documents, int topK, Request<?> request) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        var apiKey = RequestOverrides.str(request, RequestOverrides.VOYAGE_RERANK_API_KEY, defaultApiKey);
        var model = RequestOverrides.str(request, RequestOverrides.VOYAGE_RERANK_MODEL, defaultModel);
        var baseUrl = RequestOverrides.str(request, RequestOverrides.VOYAGE_RERANK_BASE_URL, defaultBaseUrl);

        var payload = buildPayload(model, query, documents, topK);
        var endpoint = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "rerank";

        var httpReq = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        try {
            var httpResp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (httpResp.statusCode() != 200) {
                throw new RuntimeException("Voyage rerank endpoint " + endpoint + " returned HTTP "
                    + httpResp.statusCode() + ": " + httpResp.body());
            }

            return RerankWireParsing.parse(httpResp.body(), "data");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Voyage rerank endpoint " + endpoint + ": " + e.getMessage(), e);
        }
    }

    /**
     * Package-private and pure so the request-building logic (in particular,
     * omitting {@code top_k} when non-positive — Voyage's default is "all
     * results," not an error, but a non-positive value on the wire is still not
     * something to send) can be unit-tested without an HTTP round-trip.
     */
    static String buildPayload(String model, String query, List<String> documents, int topK) {
        var docsJson = new StringBuilder("[");
        for (int i = 0; i < documents.size(); i++) {
            docsJson.append("\"").append(RerankWireParsing.escape(documents.get(i))).append("\"");
            if (i < documents.size() - 1) {
                docsJson.append(",");
            }
        }
        docsJson.append("]");

        var payload = new StringBuilder("{\"model\":\"")
            .append(RerankWireParsing.escape(model))
            .append("\",\"query\":\"")
            .append(RerankWireParsing.escape(query))
            .append("\",\"documents\":")
            .append(docsJson);

        if (topK > 0) {
            payload.append(",\"top_k\":").append(topK);
        }

        payload.append("}");
        return payload.toString();
    }
}
