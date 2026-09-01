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
 * {@code _ai-embedding-override=<providerName>}, attaching:
 * <ul>
 *   <li>{@code override-ai-embedding-provider} = {@code <providerName>} — read by
 *       {@code documentChunkingInterceptor}, {@code autoEmbeddingInterceptor} and
 *       {@code vectorizeOperator} (restheart-ai), which all share this override key</li>
 *   <li>{@code override-ai-voyage-api-key} and {@code override-ai-voyage-contextual-api-key}
 *       = the {@code VOYAGE_API_KEY} environment variable (never read from, or
 *       written to, any config file) — attached unconditionally; only the one actually
 *       resolved for the request (based on {@code providerName}) ever reads its key</li>
 * </ul>
 *
 * <p>Used by karate/ai/embedding-provider.feature to exercise restheart-ai's
 * embedding providers per-request, without enabling any of them — or requiring their
 * API key — for the rest of the suite: every other request keeps resolving to
 * whatever static (blank/disabled) configuration those plugins have.
 *
 * <p>Example Karate call:
 * <pre>
 *   Given path bucket + '?_ai-embedding-override=voyageContextualEmbeddingProvider'
 * </pre>
 */
@RegisterPlugin(
        name = "aiEmbeddingProviderOverrideInterceptor",
        description = "Attaches override-ai-embedding-provider=<name> per-request when ?_ai-embedding-override=<name> is present",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
        priority = 20,
        enabledByDefault = false)
public class AiEmbeddingProviderOverrideInterceptor implements WildcardInterceptor {

    private static final String QPARAM = "_ai-embedding-override";

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        var providerName = req.getExchange().getQueryParameters().get(QPARAM).peekFirst();
        if (providerName == null || providerName.isBlank()) {
            return;
        }

        req.attachParam("override-ai-embedding-provider", providerName);

        var apiKey = System.getenv("VOYAGE_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            req.attachParam("override-ai-voyage-api-key", apiKey);
            req.attachParam("override-ai-voyage-contextual-api-key", apiKey);
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        var params = req.getExchange().getQueryParameters();
        return params.containsKey(QPARAM) && !params.get(QPARAM).isEmpty();
    }
}
