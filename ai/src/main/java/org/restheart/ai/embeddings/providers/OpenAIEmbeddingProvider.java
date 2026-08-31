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
 *
 * <h2>Multi-tenant</h2>
 * <p>{@code api-key}/{@code model}/{@code base-url} above are the single-tenant
 * defaults. Per request, a deployment's tenant-config interceptor may attach
 * {@link RequestOverrides#OPENAI_API_KEY}, {@link RequestOverrides#OPENAI_MODEL},
 * {@link RequestOverrides#OPENAI_BASE_URL} to use different values for that tenant —
 * nothing is cached across requests, so there is no risk of one tenant's key leaking
 * into another tenant's call.
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

    // static, single-tenant defaults; overridden per request via RequestOverrides
    private String defaultApiKey;
    private String defaultModel;
    private String defaultBaseUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private EmbeddingModel instance;

    @OnInit
    public void init() {
        this.defaultApiKey = argOrDefault(config, "api-key", "");
        this.defaultModel = argOrDefault(config, "model", DEFAULT_MODEL);
        this.defaultBaseUrl = argOrDefault(config, "base-url", DEFAULT_BASE_URL);

        if (defaultApiKey == null || defaultApiKey.isBlank()) {
            LOGGER.warn("openAIEmbeddingProvider: no api-key configured, embedding calls will fail "
                + "unless every request overrides it via {}", RequestOverrides.OPENAI_API_KEY);
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

        var apiKey = RequestOverrides.str(request, RequestOverrides.OPENAI_API_KEY, defaultApiKey);
        var model = RequestOverrides.str(request, RequestOverrides.OPENAI_MODEL, defaultModel);
        var baseUrl = RequestOverrides.str(request, RequestOverrides.OPENAI_BASE_URL, defaultBaseUrl);

        var inputJson = new StringBuilder("[");
        for (int i = 0; i < texts.size(); i++) {
            inputJson.append("\"").append(OpenAiWireEmbeddings.escape(texts.get(i))).append("\"");
            if (i < texts.size() - 1) {
                inputJson.append(",");
            }
        }
        inputJson.append("]");

        var payload = "{\"model\":\"" + OpenAiWireEmbeddings.escape(model) + "\",\"input\":" + inputJson + "}";
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

            return parseEmbeddings(httpResp.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call embeddings endpoint " + endpoint + ": " + e.getMessage(), e);
        }
    }

    /**
     * Parses an OpenAI-wire-format {@code /v1/embeddings} response body into one
     * vector per input text. See {@link OpenAiWireEmbeddings#parse} — kept here as
     * a named delegate so the existing test suite for this class doesn't need to
     * know about the shared parser used by every OpenAI-wire-format provider.
     */
    static List<float[]> parseEmbeddings(String responseBody) {
        return OpenAiWireEmbeddings.parse(responseBody);
    }
}
