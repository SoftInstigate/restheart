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
import org.restheart.plugins.ai.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedding provider for the Voyage AI API (verified against
 * https://docs.voyageai.com/reference/embeddings-api on 2026-08-31):
 * {@code POST https://api.voyageai.com/v1/embeddings}, Bearer auth, request
 * {@code {"model", "input"}} plus an optional {@code input_type} ({@code "query"}
 * or {@code "document"}, improves retrieval quality when set), response
 * {@code {"data": [{"embedding": [...], "index": ...}]}} — the exact same shape as
 * OpenAI's {@code /v1/embeddings}, so response parsing is shared with
 * {@link OpenAIEmbeddingProvider} via {@link OpenAiWireEmbeddings}.
 *
 * <pre>{@code
 * plugins-args:
 *   voyageEmbeddingProvider:
 *     enabled: true
 *     api-key: <key>
 *     model: voyage-3.5        # optional, this is the default
 *     input-type: document     # optional: "query" or "document"; omitted by default
 * }</pre>
 *
 * <h2>Multi-tenant</h2>
 * <p>Per request, a deployment's tenant-config interceptor may attach
 * {@link RequestOverrides#VOYAGE_API_KEY}, {@link RequestOverrides#VOYAGE_MODEL},
 * {@link RequestOverrides#VOYAGE_BASE_URL}, {@link RequestOverrides#VOYAGE_INPUT_TYPE}
 * to use different values for that tenant.
 */
@RegisterPlugin(
    name = "voyageEmbeddingProvider",
    description = "Provides text embeddings via the Voyage AI API",
    enabledByDefault = false
)
public class VoyageEmbeddingProvider implements Provider<EmbeddingModel> {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoyageEmbeddingProvider.class);

    private static final String DEFAULT_BASE_URL = "https://api.voyageai.com/v1";
    private static final String DEFAULT_MODEL = "voyage-3.5";

    @Inject("config")
    private Map<String, Object> config;

    // static, single-tenant defaults; overridden per request via RequestOverrides
    private String defaultApiKey;
    private String defaultModel;
    private String defaultBaseUrl;
    private String defaultInputType;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private EmbeddingModel instance;

    @OnInit
    public void init() {
        this.defaultApiKey = argOrDefault(config, "api-key", "");
        this.defaultModel = argOrDefault(config, "model", DEFAULT_MODEL);
        this.defaultBaseUrl = argOrDefault(config, "base-url", DEFAULT_BASE_URL);
        this.defaultInputType = argOrDefault(config, "input-type", "");

        if (defaultApiKey == null || defaultApiKey.isBlank()) {
            LOGGER.warn("voyageEmbeddingProvider: no api-key configured, embedding calls will fail "
                + "unless every request overrides it via {}", RequestOverrides.VOYAGE_API_KEY);
        }

        this.instance = this::embed;
    }

    @Override
    public EmbeddingModel get(final PluginRecord<?> caller) {
        return instance;
    }

    private List<float[]> embed(List<String> texts, Request<?> request) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        var apiKey = RequestOverrides.str(request, RequestOverrides.VOYAGE_API_KEY, defaultApiKey);
        var model = RequestOverrides.str(request, RequestOverrides.VOYAGE_MODEL, defaultModel);
        var baseUrl = RequestOverrides.str(request, RequestOverrides.VOYAGE_BASE_URL, defaultBaseUrl);
        var inputType = RequestOverrides.str(request, RequestOverrides.VOYAGE_INPUT_TYPE, defaultInputType);

        var payload = buildPayload(model, texts, inputType);
        var endpoint = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "embeddings";

        var httpReq = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        try {
            var httpResp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (httpResp.statusCode() != 200) {
                throw new RuntimeException("Voyage embeddings endpoint " + endpoint + " returned HTTP "
                    + httpResp.statusCode() + ": " + httpResp.body());
            }

            return OpenAiWireEmbeddings.parse(httpResp.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Voyage embeddings endpoint " + endpoint + ": " + e.getMessage(), e);
        }
    }

    /**
     * Builds the request payload, including the optional {@code input_type} field
     * only when configured — Voyage treats a present-but-unrecognized value as an
     * error, so an empty/unset {@code inputType} must omit the field entirely
     * rather than send it as {@code ""}. Package-private and pure so this
     * provider-specific detail (the one thing that differs from plain
     * OpenAI-wire-format providers) can be unit-tested without an HTTP round-trip.
     */
    static String buildPayload(String model, List<String> texts, String inputType) {
        var inputJson = new StringBuilder("[");
        for (int i = 0; i < texts.size(); i++) {
            inputJson.append("\"").append(OpenAiWireEmbeddings.escape(texts.get(i))).append("\"");
            if (i < texts.size() - 1) {
                inputJson.append(",");
            }
        }
        inputJson.append("]");

        var payload = new StringBuilder("{\"model\":\"")
            .append(OpenAiWireEmbeddings.escape(model))
            .append("\",\"input\":")
            .append(inputJson);

        if (inputType != null && !inputType.isBlank()) {
            payload.append(",\"input_type\":\"").append(OpenAiWireEmbeddings.escape(inputType)).append("\"");
        }

        payload.append("}");
        return payload.toString();
    }
}
