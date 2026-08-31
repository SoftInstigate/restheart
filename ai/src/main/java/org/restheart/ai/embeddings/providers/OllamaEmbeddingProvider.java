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
 * Embedding provider for a local or self-hosted Ollama server's
 * {@code POST /api/embed} endpoint (verified against
 * https://docs.ollama.com/api/embed on 2026-08-31): request
 * {@code {"model", "input"}} (a string or an array of strings), response
 * {@code {"model", "embeddings": [[...], ...]}} — no auth, embeddings returned in
 * the same order as the input array (unlike the OpenAI wire format, there is no
 * per-item {@code index} to re-sort by).
 *
 * <pre>{@code
 * plugins-args:
 *   ollamaEmbeddingProvider:
 *     enabled: true
 *     base-url: http://localhost:11434   # optional, this is the default
 *     model: nomic-embed-text            # optional, this is the default
 * }</pre>
 */
@RegisterPlugin(
    name = "ollamaEmbeddingProvider",
    description = "Provides text embeddings via a local or self-hosted Ollama server",
    enabledByDefault = false
)
public class OllamaEmbeddingProvider implements Provider<EmbeddingModel> {
    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaEmbeddingProvider.class);

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "nomic-embed-text";

    @Inject("config")
    private Map<String, Object> config;

    private String model;
    private String baseUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private EmbeddingModel instance;

    @OnInit
    public void init() {
        this.model = argOrDefault(config, "model", DEFAULT_MODEL);
        this.baseUrl = argOrDefault(config, "base-url", DEFAULT_BASE_URL);

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
        var endpoint = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "api/embed";

        var httpReq = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        try {
            var httpResp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (httpResp.statusCode() != 200) {
                throw new RuntimeException("Ollama embeddings endpoint " + endpoint + " returned HTTP "
                    + httpResp.statusCode() + ": " + httpResp.body());
            }

            return parseEmbeddings(httpResp.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Ollama embeddings endpoint " + endpoint + ": " + e.getMessage(), e);
        }
    }

    /**
     * Parses an Ollama {@code /api/embed} response body ({@code {"embeddings":
     * [[...], ...]}}) into one vector per input text, in the same order as
     * returned (Ollama already preserves input order, unlike the OpenAI wire
     * format's {@code index}-tagged entries). Pulled out of {@link #embed} so the
     * parsing logic can be unit-tested without an HTTP round-trip.
     */
    static List<float[]> parseEmbeddings(String responseBody) {
        var respDoc = BsonDocument.parse(responseBody);
        var embeddings = respDoc.getArray("embeddings");

        var result = new ArrayList<float[]>(embeddings.size());
        for (var item : embeddings) {
            var vector = item.asArray();
            var embedding = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                embedding[i] = (float) vector.get(i).asNumber().doubleValue();
            }
            result.add(embedding);
        }
        return result;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
