package org.restheart.test.plugins.providers;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic vectors for the fake embedding providers: the same text always gives the same
 * vector, different texts almost always differ, and the length is whatever the provider says.
 *
 * <p>A helper, not a provider: RESTHeart's plugin scanner sees public classes only, and a class
 * that reaches {@code Provider} through a non-public superclass is not found at all. Every fake
 * provider therefore implements {@code Provider<EmbeddingModel>} itself, publicly, and shares
 * only this.
 */
public final class FakeVectors {

    private FakeVectors() {
    }

    public static List<float[]> embed(List<String> texts, int dimensions) {
        var vectors = new ArrayList<float[]>(texts.size());
        for (var text : texts) {
            vectors.add(vectorOf(text, dimensions));
        }
        return vectors;
    }

    static float[] vectorOf(String text, int dimensions) {
        var vector = new float[dimensions];
        var seed = text == null ? 0 : text.hashCode();
        for (int i = 0;i < dimensions;i++) {
            seed = seed * 31 + i + 1;
            vector[i] = ((seed >>> 8) & 0xff) / 255f;
        }
        return vector;
    }
}
