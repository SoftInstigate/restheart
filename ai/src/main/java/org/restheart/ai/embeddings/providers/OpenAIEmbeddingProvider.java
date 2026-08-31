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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.ai.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedding provider for any endpoint that speaks the OpenAI {@code /v1/embeddings}
 * wire format: {@code {"model", "input"}} request, {@code {"data": [{"embedding",
 * "index"}]}} response, Bearer auth. This covers OpenAI itself as well as
 * OpenAI-compatible gateways such as OpenRouter (verified against
 * https://openrouter.ai/docs/api_reference/embeddings on 2026-08-31 — same request
 * and response shape at {@code https://openrouter.ai/api/v1/embeddings}), Together.ai,
 * Groq, or a self-hosted OpenAI-compatible server — by pointing {@code base-url}
 * elsewhere. No per-gateway code or dependency is needed because the wire shape is
 * shared; this is unlike a proprietary API (e.g. AWS Bedrock), which needs its own
 * provider implementation.
 *
 * <pre>{@code
 * plugins-args:
 *   openAIEmbeddingProvider:
 *     enabled: true
 *     api-key: <key>
 *     model: text-embedding-3-small           # or e.g. openai/text-embedding-3-small on OpenRouter
 *     base-url: https://api.openai.com/v1     # optional; e.g. https://openrouter.ai/api/v1
 * }</pre>
 */
@RegisterPlugin(
    name = "openAIEmbeddingProvider",
    description = "Provides text embeddings via any OpenAI-compatible /v1/embeddings endpoint",
    enabledByDefault = false
)
public class OpenAIEmbeddingProvider implements Provider<EmbeddingModel> {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAIEmbeddingProvider.class);

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "text-embedding-3-small";

    @Inject("config")
    private Map<String, Object> config;

    private String apiKey;
    private String model;
    private String baseUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private EmbeddingModel instance;

    @OnInit
    public void init() {
        this.apiKey = argOrDefault(config, "api-key", "");
        this.model = argOrDefault(config, "model", DEFAULT_MODEL);
        this.baseUrl = argOrDefault(config, "base-url", DEFAULT_BASE_URL);

        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.warn("openAIEmbeddingProvider: no api-key configured, embedding calls will fail");
        }

        this.instance = this::embed;
    }

    @Override
    public EmbeddingModel get(final PluginRecord<?> caller) {
        return instance;
    }

    private List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        var inputJson = new StringBuilder("[");
        for (int i = 0; i < texts.size(); i++) {
            inputJson.append("\"").append(escape(texts.get(i))).append("\"");
            if (i < texts.size() - 1) {
                inputJson.append(",");
            }
        }
        inputJson.append("]");

        var payload = "{\"model\":\"" + escape(model) + "\",\"input\":" + inputJson + "}";
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
                throw new RuntimeException("Embeddings endpoint " + endpoint + " returned HTTP "
                    + httpResp.statusCode() + ": " + httpResp.body());
            }

            var respDoc = BsonDocument.parse(httpResp.body());
            var data = respDoc.getArray("data");

            // entries are not guaranteed to be returned in request order
            var ordered = new float[data.size()][];
            for (var item : data) {
                var doc = item.asDocument();
                var idx = doc.getInt32("index").getValue();
                var vector = doc.getArray("embedding");
                var embedding = new float[vector.size()];
                for (int i = 0; i < vector.size(); i++) {
                    embedding[i] = (float) vector.get(i).asNumber().doubleValue();
                }
                ordered[idx] = embedding;
            }

            var result = new ArrayList<float[]>(ordered.length);
            for (var embedding : ordered) {
                result.add(embedding);
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call embeddings endpoint " + endpoint + ": " + e.getMessage(), e);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
