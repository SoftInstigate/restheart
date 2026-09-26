package org.restheart.test.plugins.providers;

import java.util.ArrayList;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.ai.RankedResult;
import org.restheart.plugins.ai.RerankModel;

/**
 * A reranking provider that calls nobody: it returns the documents in reverse order, the last
 * first, with scores 1, 0.9, 0.8, and so on, and at most {@code topK} of them. The reverse of what
 * the pipeline returned is an order no real search produces by chance, so a test knows the
 * reranker ran from the order alone, and from the {@code _rerankScore} it adds.
 */
@RegisterPlugin(
        name = "fakeRerankProvider",
        description = "Reranking provider for tests: the documents in reverse order, no network",
        enabledByDefault = false)
public class FakeRerankProvider implements Provider<RerankModel> {

    @Override
    public RerankModel get(final PluginRecord<?> caller) {
        return (query, documents, topK, request) -> {
            var ranked = new ArrayList<RankedResult>();
            for (int i = documents.size() - 1, rank = 0; i >= 0 && rank < topK; i--, rank++) {
                ranked.add(new RankedResult(i, 1.0 - rank * 0.1));
            }
            return ranked;
        };
    }
}
