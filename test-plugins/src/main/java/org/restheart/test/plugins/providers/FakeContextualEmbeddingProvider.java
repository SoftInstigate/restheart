package org.restheart.test.plugins.providers;

import java.util.ArrayList;
import java.util.List;

import org.restheart.exchange.Request;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.ai.ContextualEmbeddingModel;
import org.restheart.plugins.ai.EmbeddingModel;

/**
 * A contextual embedding provider that calls nobody and says what it was given: embedding a group
 * of chunks together, it answers for each chunk the vector {@code [size of the group, position in
 * the group]}; embedding texts on their own, {@code [1, 0]} each. A test reads from the vectors
 * whether the chunks of a file were embedded as one document (#754).
 */
@RegisterPlugin(
        name = "fakeContextualEmbeddingProvider",
        description = "Contextual embedding provider for tests: [group size, position] per chunk, no network",
        enabledByDefault = false)
public class FakeContextualEmbeddingProvider implements Provider<EmbeddingModel> {

    @Override
    public EmbeddingModel get(final PluginRecord<?> caller) {
        return new Model();
    }

    private static final class Model implements EmbeddingModel, ContextualEmbeddingModel {
        @Override
        public List<float[]> embed(List<String> texts, Request<?> request) {
            return texts.stream().map(t -> new float[] { 1f, 0f }).toList();
        }

        @Override
        public List<float[]> embedChunks(List<String> chunks, Request<?> request) {
            var vectors = new ArrayList<float[]>(chunks.size());
            for (int i = 0;i < chunks.size();i++) {
                vectors.add(new float[] { chunks.size(), i });
            }
            return vectors;
        }
    }
}
