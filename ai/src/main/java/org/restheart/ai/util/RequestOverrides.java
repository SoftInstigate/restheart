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
package org.restheart.ai.util;

import org.restheart.exchange.Request;

/**
 * Reads per-request override parameters and returns the effective value, falling back
 * to the plugin's own static configuration. Follows the same {@code override-<module>-*}
 * convention as {@code restheart-stripe}'s and {@code restheart-accounts}' own
 * {@code RequestOverrides} classes.
 *
 * <h2>Multi-tenant usage</h2>
 * <p>A deployment's own interceptor reads per-tenant configuration (e.g. from MongoDB)
 * and calls {@link Request#attachParam(String, Object)} with these keys at
 * {@code REQUEST_BEFORE_EXCHANGE_INIT}, before any {@code restheart-ai} plugin runs.
 * None of that tenant-resolution logic lives in this module — it only defines and reads
 * the keys.
 *
 * <h2>Single-tenant usage</h2>
 * <p>When no interceptor attaches override params, every accessor here returns the
 * plugin's own statically configured value, preserving single-tenant behavior exactly.
 *
 * <h2>Why this exists</h2>
 * <p>Credentials and per-tenant settings (API keys, base URLs, chunking parameters) must
 * never be cached once at plugin {@code @OnInit} time into a shared instance if they are
 * meant to be tenant-overridable — a request from tenant B would otherwise see tenant
 * A's cached key. Every value here is resolved fresh per request, at the point of actual
 * use (e.g. inside {@code EmbeddingModel.embed(texts, request)} or an interceptor's
 * {@code handle()}), exactly like {@code restheart-stripe}'s secret key is never cached
 * into a shared Stripe client and is instead passed explicitly to every SDK call.
 */
public final class RequestOverrides {

    // ── openAIEmbeddingProvider overrides (also covers OpenRouter/any OpenAI-wire gateway) ──
    public static final String OPENAI_API_KEY = "override-ai-openai-api-key";
    public static final String OPENAI_MODEL = "override-ai-openai-model";
    public static final String OPENAI_BASE_URL = "override-ai-openai-base-url";

    // ── voyageEmbeddingProvider overrides ────────────────────────────────────
    public static final String VOYAGE_API_KEY = "override-ai-voyage-api-key";
    public static final String VOYAGE_MODEL = "override-ai-voyage-model";
    public static final String VOYAGE_BASE_URL = "override-ai-voyage-base-url";
    public static final String VOYAGE_INPUT_TYPE = "override-ai-voyage-input-type";

    // ── ollamaEmbeddingProvider overrides ────────────────────────────────────
    public static final String OLLAMA_BASE_URL = "override-ai-ollama-base-url";
    public static final String OLLAMA_MODEL = "override-ai-ollama-model";

    // ── cohereRerankProvider overrides ───────────────────────────────────────
    public static final String COHERE_API_KEY = "override-ai-cohere-api-key";
    public static final String COHERE_MODEL = "override-ai-cohere-model";
    public static final String COHERE_BASE_URL = "override-ai-cohere-base-url";

    // ── voyageRerankProvider overrides ───────────────────────────────────────
    // (kept distinct from VOYAGE_API_KEY etc. above — a tenant may use a different
    // Voyage key/model for reranking than for embeddings)
    public static final String VOYAGE_RERANK_API_KEY = "override-ai-voyage-rerank-api-key";
    public static final String VOYAGE_RERANK_MODEL = "override-ai-voyage-rerank-model";
    public static final String VOYAGE_RERANK_BASE_URL = "override-ai-voyage-rerank-base-url";

    // ── autoEmbeddingInterceptor overrides ───────────────────────────────────
    /** Overrides which {@code Provider<EmbeddingModel>} plugin name to use for this request. */
    public static final String EMBEDDING_PROVIDER = "override-ai-embedding-provider";

    // ── documentChunkingInterceptor overrides ────────────────────────────────
    public static final String CHUNK_SIZE = "override-ai-chunk-size";
    public static final String CHUNK_OVERLAP = "override-ai-chunk-overlap";
    public static final String TARGET_COLLECTION = "override-ai-target-collection";

    // ── rerankingInterceptor overrides ───────────────────────────────────────
    public static final String ATLAS_API_KEY = "override-ai-atlas-api-key";
    public static final String RERANK_API_URL = "override-ai-rerank-api-url";
    /**
     * Overrides which {@code Provider<RerankModel>} plugin name to use for this request
     * (e.g. {@code cohereRerankProvider}, {@code voyageRerankProvider}). When blank
     * (no override, no static {@code rerank-provider} configured), reranking falls back
     * to the Atlas Reranking API directly — Phase 1 behavior, unchanged.
     */
    public static final String RERANK_PROVIDER = "override-ai-rerank-provider";

    private RequestOverrides() {
    }

    // ── Generic accessors ─────────────────────────────────────────────────────

    /** Effective string value: the attached override if a non-blank String, else {@code defaultValue}. */
    public static String str(Request<?> req, String key, String defaultValue) {
        if (req == null) {
            return defaultValue;
        }
        var v = req.attachedParam(key);
        return (v instanceof String s && !s.isBlank()) ? s : defaultValue;
    }

    /**
     * Effective int value. Accepts either an attached {@code Integer} or a numeric
     * {@code String} (a deployment's tenant-config interceptor may attach either,
     * depending on how it parsed its source document).
     */
    public static int intVal(Request<?> req, String key, int defaultValue) {
        if (req == null) {
            return defaultValue;
        }
        var v = req.attachedParam(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.strip());
            } catch (NumberFormatException nfe) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
