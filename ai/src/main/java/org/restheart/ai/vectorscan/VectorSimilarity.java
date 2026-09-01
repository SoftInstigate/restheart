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
package org.restheart.ai.vectorscan;

/**
 * Pure vector-distance math for {@code $vectorScan} — no MongoDB, no index,
 * just arithmetic. Mirrors the {@code similarity} values MongoDB's own
 * {@code $vectorSearch} index field type accepts ({@code cosine}, {@code dotProduct},
 * {@code euclidean}), but defines its own score convention rather than trying to
 * replicate Atlas's internal {@code $meta: "vectorSearchScore"} value: for every
 * metric here, a <strong>higher score always means more similar</strong>, so callers
 * can sort descending regardless of which metric was used. Since raw Euclidean
 * distance is smaller-is-better, {@link #EUCLIDEAN} negates it to fit that convention.
 */
public final class VectorSimilarity {
    private VectorSimilarity() {
    }

    public static final String COSINE = "cosine";
    public static final String DOT_PRODUCT = "dotProduct";
    public static final String EUCLIDEAN = "euclidean";

    /**
     * @param similarity one of {@link #COSINE}, {@link #DOT_PRODUCT}, {@link #EUCLIDEAN}
     * @throws IllegalArgumentException if {@code similarity} is none of the above, or
     *         if {@code a}/{@code b} have different lengths
     */
    public static double score(String similarity, float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("vector length mismatch: " + a.length + " vs " + b.length);
        }
        return switch (similarity) {
            case COSINE -> cosine(a, b);
            case DOT_PRODUCT -> dotProduct(a, b);
            case EUCLIDEAN -> -euclideanDistance(a, b);
            default -> throw new IllegalArgumentException(
                "unknown similarity '" + similarity + "', expected one of: cosine, dotProduct, euclidean");
        };
    }

    static double dotProduct(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    static double cosine(float[] a, float[] b) {
        var dot = dotProduct(a, b);
        var normA = Math.sqrt(dotProduct(a, a));
        var normB = Math.sqrt(dotProduct(b, b));
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (normA * normB);
    }

    static double euclideanDistance(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            var diff = (double) a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
