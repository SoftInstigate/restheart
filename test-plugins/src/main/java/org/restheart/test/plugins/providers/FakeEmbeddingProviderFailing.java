package org.restheart.test.plugins.providers;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.ai.EmbeddingModel;

/**
 * An embedding provider whose every call fails, the way a real one does when the vendor is down
 * or the key is wrong. Lets the suite verify that a write whose embedding fails is still written,
 * without the vector and with a warning, rather than refused.
 */
@RegisterPlugin(
        name = "fakeEmbeddingProviderFailing",
        description = "Embedding provider for tests: every call fails",
        enabledByDefault = false)
public class FakeEmbeddingProviderFailing implements Provider<EmbeddingModel> {

    @Override
    public EmbeddingModel get(final PluginRecord<?> caller) {
        return (texts, request) -> {
            throw new IllegalStateException("the embedding vendor is unreachable");
        };
    }
}
