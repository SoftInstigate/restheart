package org.restheart.test.plugins.interceptors;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

/**
 * Test interceptor that simulates a deployment-layer interceptor attaching a
 * per-tenant embedding-provider override to the request.
 *
 * <p>Activates only when the request carries the query parameter
 * {@code _ai-voyage-contextual-override=1}, attaching:
 * <ul>
 *   <li>{@code override-ai-embedding-provider} = {@code voyageContextualEmbeddingProvider}</li>
 *   <li>{@code override-ai-voyage-contextual-api-key} = the {@code VOYAGE_API_KEY}
 *       environment variable (never read from, or written to, any config file)</li>
 * </ul>
 *
 * <p>Used by karate/ai/embedding-provider.feature to exercise
 * {@code voyageContextualEmbeddingProvider} without enabling it — or requiring its
 * API key — for the rest of the suite: {@code documentChunkingInterceptor} stays on
 * its static (blank {@code embedding-provider}) configuration for every other
 * request; only requests carrying this query param resolve to this provider.
 *
 * <p>Example Karate call:
 * <pre>
 *   Given path bucket + '?_ai-voyage-contextual-override=1'
 * </pre>
 */
@RegisterPlugin(
        name = "aiVoyageContextualOverrideInterceptor",
        description = "Attaches override-ai-embedding-provider=voyageContextualEmbeddingProvider per-request when ?_ai-voyage-contextual-override=1 is present",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
        priority = 20,
        enabledByDefault = false)
public class AiVoyageContextualOverrideInterceptor implements WildcardInterceptor {

    private static final String QPARAM = "_ai-voyage-contextual-override";

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        req.attachParam("override-ai-embedding-provider", "voyageContextualEmbeddingProvider");

        var apiKey = System.getenv("VOYAGE_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            req.attachParam("override-ai-voyage-contextual-api-key", apiKey);
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        var params = req.getExchange().getQueryParameters();
        return params.containsKey(QPARAM) && !params.get(QPARAM).isEmpty();
    }
}
