package org.restheart.test.plugins.interceptors;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

/**
 * Test interceptor that simulates a deployment-layer interceptor attaching
 * per-tenant embedding/rerank provider overrides to the request.
 *
 * <p>Activates when the request carries either query parameter:
 * <ul>
 *   <li>{@code _ai-embedding-override=<providerName>} attaches:
 *     <ul>
 *       <li>{@code override-ai-embedding-provider} = {@code <providerName>} — read by
 *           {@code documentChunkingInterceptor}, {@code autoEmbeddingInterceptor} and
 *           {@code vectorizeOperator} (restheart-ai), which all share this override key</li>
 *       <li>{@code override-ai-voyage-api-key} and {@code override-ai-voyage-contextual-api-key}
 *           = the {@code VOYAGE_API_KEY} environment variable — attached unconditionally;
 *           only the one actually resolved for the request ever reads its key</li>
 *     </ul>
 *   </li>
 *   <li>{@code _ai-rerank-override=<providerName>} attaches:
 *     <ul>
 *       <li>{@code override-ai-rerank-provider} = {@code <providerName>} — read by
 *           {@code rerankingInterceptor}</li>
 *       <li>{@code override-ai-voyage-rerank-api-key} = the same {@code VOYAGE_API_KEY}
 *           environment variable (never read from, or written to, any config file)</li>
 *     </ul>
 *   </li>
 * </ul>
 * Both can be present on the same request (e.g. a live semantic-search-plus-rerank
 * query needs an embedding provider to resolve {@code $vectorize} and a rerank
 * provider to resolve the {@code rerank} aggregation attribute).
 *
 * <p>Used by karate/ai/embedding-provider.feature and
 * karate/ai/live-semantic-search.feature to exercise restheart-ai's providers
 * per-request, without enabling any of them — or requiring their API key — for the
 * rest of the suite: every other request keeps resolving to whatever static
 * (blank/disabled) configuration those plugins have.
 *
 * <p>Example Karate call:
 * <pre>
 *   Given path bucket
 *   And param _ai-embedding-override = 'voyageContextualEmbeddingProvider'
 *   And param _ai-rerank-override = 'voyageRerankProvider'
 * </pre>
 */
@RegisterPlugin(
        name = "aiEmbeddingProviderOverrideInterceptor",
        description = "Attaches override-ai-embedding-provider=<name> and/or override-ai-rerank-provider=<name> per-request when ?_ai-embedding-override=<name> and/or ?_ai-rerank-override=<name> is present",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
        priority = 20,
        enabledByDefault = false)
public class AiEmbeddingProviderOverrideInterceptor implements WildcardInterceptor {

    private static final String EMBEDDING_QPARAM = "_ai-embedding-override";
    private static final String RERANK_QPARAM = "_ai-rerank-override";

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        var params = req.getExchange().getQueryParameters();
        var apiKey = System.getenv("VOYAGE_API_KEY");

        var embeddingProviderName = params.containsKey(EMBEDDING_QPARAM) ? params.get(EMBEDDING_QPARAM).peekFirst() : null;
        if (embeddingProviderName != null && !embeddingProviderName.isBlank()) {
            req.attachParam("override-ai-embedding-provider", embeddingProviderName);
            if (apiKey != null && !apiKey.isBlank()) {
                req.attachParam("override-ai-voyage-api-key", apiKey);
                req.attachParam("override-ai-voyage-contextual-api-key", apiKey);
            }
        }

        var rerankProviderName = params.containsKey(RERANK_QPARAM) ? params.get(RERANK_QPARAM).peekFirst() : null;
        if (rerankProviderName != null && !rerankProviderName.isBlank()) {
            req.attachParam("override-ai-rerank-provider", rerankProviderName);
            if (apiKey != null && !apiKey.isBlank()) {
                req.attachParam("override-ai-voyage-rerank-api-key", apiKey);
            }
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        var params = req.getExchange().getQueryParameters();
        return (params.containsKey(EMBEDDING_QPARAM) && !params.get(EMBEDDING_QPARAM).isEmpty())
            || (params.containsKey(RERANK_QPARAM) && !params.get(RERANK_QPARAM).isEmpty());
    }
}
