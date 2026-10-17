package org.restheart.test.plugins.providers;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.ai.EmbeddingModel;

/**
 * An embedding provider that calls nobody: deterministic vectors of length 5, from
 * {@link FakeVectors}, so the integration suite can prove that restheart-ai writes and reads
 * vectors without a real provider, an API key or a network. Its sibling of another length lets
 * a test tell which provider embedded a field from the length of its vector: the way a
 * collection with two embedding rules on two providers is verified (#752).
 */
@RegisterPlugin(
        name = "fakeEmbeddingProvider5d",
        description = "Embedding provider for tests: deterministic vectors of length 5, no network",
        enabledByDefault = false)
public class FakeEmbeddingProvider5d implements Provider<EmbeddingModel> {

    @Override
    public EmbeddingModel get(final PluginRecord<?> caller) {
        return (texts, request) -> FakeVectors.embed(texts, 5);
    }
}
