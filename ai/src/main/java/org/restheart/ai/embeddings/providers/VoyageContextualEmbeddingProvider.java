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
package org.restheart.ai.embeddings.providers;

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
import org.restheart.plugins.ai.ContextualEmbeddingModel;
import org.restheart.plugins.ai.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedding provider for Voyage AI's contextualized chunk embeddings API (verified
 * against https://docs.voyageai.com/docs/contextualized-chunk-embeddings on
 * 2026-09-01): {@code POST https://api.voyageai.com/v1/contextualizedembeddings},
 * Bearer auth, request {@code {"model", "inputs": [[chunk, chunk, ...], ...]}} plus
 * optional {@code input_type} ({@code "query"} or {@code "document"}) and
 * {@code output_dimension} (256/512/1024/2048, 1024 default), response
 * {@code {"data": [{"data": [{"embedding": [...], "index": ...}, ...], "index": ...},
 * ...]}} — one outer entry per input group, each with its own inner per-chunk
 * entries (see {@link VoyageContextualWireEmbeddings}).
 *
 * <p>Only one model currently supports this endpoint: {@code voyage-context-4}
 * (32k-token per-chunk context window). Kept as a separate provider from
 * {@link VoyageEmbeddingProvider} (plain {@code /v1/embeddings}, default
 * {@code voyage-3.5}) rather than a mode switch on it, since the two speak
 * different wire formats end to end (request shape, response shape, endpoint).
 *
 * <p>Implements both {@link EmbeddingModel} — each input text sent as its own
 * single-chunk group, i.e. independently, for callers like
 * {@code autoEmbeddingInterceptor}/{@code $vectorize} that batch otherwise-unrelated
 * texts in one call — and {@link ContextualEmbeddingModel} — every chunk of one
 * document sent together as a single group, for {@code documentChunkingInterceptor},
 * which is the actual point of this provider.
 *
 * <pre>{@code
 * plugins-args:
 *   voyageContextualEmbeddingProvider:
 *     enabled: true
 *     api-key: <key>
 *     model: voyage-context-4     # optional, only supported model, also the default
 *     input-type: document        # optional: "query" or "document"; omitted by default
 *     output-dimension: 1024      # optional: 256, 512, 1024 (default) or 2048
 * }</pre>
 *
 * <h2>Multi-tenant</h2>
 * <p>Per request, a deployment's tenant-config interceptor may attach
 * {@link RequestOverrides#VOYAGE_CONTEXTUAL_API_KEY}, {@link RequestOverrides#VOYAGE_CONTEXTUAL_MODEL},
 * {@link RequestOverrides#VOYAGE_CONTEXTUAL_BASE_URL}, {@link RequestOverrides#VOYAGE_CONTEXTUAL_INPUT_TYPE},
 * {@link RequestOverrides#VOYAGE_CONTEXTUAL_OUTPUT_DIMENSION} to use different values for that tenant.
 */
@RegisterPlugin(
    name = "voyageContextualEmbeddingProvider",
    description = "Provides contextualized chunk embeddings via the Voyage AI API (voyage-context-4)",
    enabledByDefault = false
)
public class VoyageContextualEmbeddingProvider implements Provider<EmbeddingModel> {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoyageContextualEmbeddingProvider.class);

    private static final String DEFAULT_BASE_URL = "https://api.voyageai.com/v1";
    private static final String DEFAULT_MODEL = "voyage-context-4";

    @Inject("config")
    private Map<String, Object> config;

    // static, single-tenant defaults; overridden per request via RequestOverrides
    private String defaultApiKey;
    private String defaultModel;
    private String defaultBaseUrl;
    private String defaultInputType;
    private int defaultOutputDimension;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private Model instance;

    @OnInit
    public void init() {
        this.defaultApiKey = argOrDefault(config, "api-key", "");
        this.defaultModel = argOrDefault(config, "model", DEFAULT_MODEL);
        this.defaultBaseUrl = argOrDefault(config, "base-url", DEFAULT_BASE_URL);
        this.defaultInputType = argOrDefault(config, "input-type", "");
        this.defaultOutputDimension = argOrDefault(config, "output-dimension", 0);

        if (defaultApiKey == null || defaultApiKey.isBlank()) {
            LOGGER.warn("voyageContextualEmbeddingProvider: no api-key configured, embedding calls will fail "
                + "unless every request overrides it via {}", RequestOverrides.VOYAGE_CONTEXTUAL_API_KEY);
        }

        this.instance = new Model();
    }

    @Override
    public EmbeddingModel get(final PluginRecord<?> caller) {
        return instance;
    }

    private final class Model implements EmbeddingModel, ContextualEmbeddingModel {
        @Override
        public List<float[]> embed(List<String> texts, Request<?> request) {
            if (texts == null || texts.isEmpty()) {
                return List.of();
            }
            return call(texts.stream().map(List::of).toList(), request, false);
        }

        @Override
        public List<float[]> embedChunks(List<String> chunksOfSameDocument, Request<?> request) {
            if (chunksOfSameDocument == null || chunksOfSameDocument.isEmpty()) {
                return List.of();
            }
            return call(List.of(chunksOfSameDocument), request, true);
        }
    }

    private List<float[]> call(List<List<String>> groups, Request<?> request, boolean grouped) {
        var apiKey = RequestOverrides.str(request, RequestOverrides.VOYAGE_CONTEXTUAL_API_KEY, defaultApiKey);
        var model = RequestOverrides.str(request, RequestOverrides.VOYAGE_CONTEXTUAL_MODEL, defaultModel);
        var baseUrl = RequestOverrides.str(request, RequestOverrides.VOYAGE_CONTEXTUAL_BASE_URL, defaultBaseUrl);
        var inputType = RequestOverrides.str(request, RequestOverrides.VOYAGE_CONTEXTUAL_INPUT_TYPE, defaultInputType);
        var outputDimension = RequestOverrides.intVal(request, RequestOverrides.VOYAGE_CONTEXTUAL_OUTPUT_DIMENSION, defaultOutputDimension);

        var payload = buildPayload(model, groups, inputType, outputDimension);
        var endpoint = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "contextualizedembeddings";

        var httpReq = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        try {
            var httpResp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (httpResp.statusCode() != 200) {
                throw new RuntimeException("Voyage contextualized embeddings endpoint " + endpoint + " returned HTTP "
                    + httpResp.statusCode() + ": " + httpResp.body());
            }

            return grouped
                ? VoyageContextualWireEmbeddings.parseChunksOfOneDocument(httpResp.body())
                : VoyageContextualWireEmbeddings.parseIndependent(httpResp.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Voyage contextualized embeddings endpoint " + endpoint + ": " + e.getMessage(), e);
        }
    }

    /**
     * Builds the request payload. {@code groups} is either every input text as its
     * own single-element group (independent embeddings) or a single group holding
     * every chunk of one document (contextualized embeddings) — see the two {@link
     * Model} methods. {@code input_type}/{@code output_dimension} are included only
     * when configured, same rationale as {@link VoyageEmbeddingProvider#buildPayload}.
     * Package-private and pure so this is unit-testable without an HTTP round-trip.
     */
    static String buildPayload(String model, List<List<String>> groups, String inputType, int outputDimension) {
        var inputsJson = new StringBuilder("[");
        for (int g = 0; g < groups.size(); g++) {
            var group = groups.get(g);
            inputsJson.append("[");
            for (int i = 0; i < group.size(); i++) {
                inputsJson.append("\"").append(OpenAiWireEmbeddings.escape(group.get(i))).append("\"");
                if (i < group.size() - 1) {
                    inputsJson.append(",");
                }
            }
            inputsJson.append("]");
            if (g < groups.size() - 1) {
                inputsJson.append(",");
            }
        }
        inputsJson.append("]");

        var payload = new StringBuilder("{\"model\":\"")
            .append(OpenAiWireEmbeddings.escape(model))
            .append("\",\"inputs\":")
            .append(inputsJson);

        if (inputType != null && !inputType.isBlank()) {
            payload.append(",\"input_type\":\"").append(OpenAiWireEmbeddings.escape(inputType)).append("\"");
        }
        if (outputDimension > 0) {
            payload.append(",\"output_dimension\":").append(outputDimension);
        }

        payload.append("}");
        return payload.toString();
    }
}
