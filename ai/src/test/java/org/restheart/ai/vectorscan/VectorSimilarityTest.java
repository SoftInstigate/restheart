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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class VectorSimilarityTest {

    private static final float[] A = {1f, 0f, 0f};
    private static final float[] B = {0f, 1f, 0f};
    private static final float[] SAME_AS_A = {1f, 0f, 0f};
    private static final float[] OPPOSITE_OF_A = {-1f, 0f, 0f};

    @Test
    public void cosine_identicalVectors_scoresOne() {
        assertEquals(1.0, VectorSimilarity.score(VectorSimilarity.COSINE, A, SAME_AS_A), 1e-9);
    }

    @Test
    public void cosine_orthogonalVectors_scoresZero() {
        assertEquals(0.0, VectorSimilarity.score(VectorSimilarity.COSINE, A, B), 1e-9);
    }

    @Test
    public void cosine_oppositeVectors_scoresNegativeOne() {
        assertEquals(-1.0, VectorSimilarity.score(VectorSimilarity.COSINE, A, OPPOSITE_OF_A), 1e-9);
    }

    @Test
    public void cosine_zeroVector_scoresZeroRatherThanDivideByZero() {
        var zero = new float[] {0f, 0f, 0f};
        assertEquals(0.0, VectorSimilarity.score(VectorSimilarity.COSINE, A, zero), 1e-9);
    }

    @Test
    public void dotProduct_matchesPlainDotProduct() {
        var a = new float[] {1f, 2f, 3f};
        var b = new float[] {4f, 5f, 6f};
        // 1*4 + 2*5 + 3*6 = 32
        assertEquals(32.0, VectorSimilarity.score(VectorSimilarity.DOT_PRODUCT, a, b), 1e-9);
    }

    @Test
    public void euclidean_identicalVectors_scoresZero() {
        // distance 0, negated is still 0 -- the "best possible" euclidean score
        assertEquals(0.0, VectorSimilarity.score(VectorSimilarity.EUCLIDEAN, A, SAME_AS_A), 1e-9);
    }

    @Test
    public void euclidean_isNegatedSoHigherScoreMeansCloser() {
        var near = new float[] {1f, 0.1f, 0f};
        var far = new float[] {1f, 5f, 0f};

        var nearScore = VectorSimilarity.score(VectorSimilarity.EUCLIDEAN, A, near);
        var farScore = VectorSimilarity.score(VectorSimilarity.EUCLIDEAN, A, far);

        assertTrue(nearScore > farScore, "the closer vector must score higher under the negated-distance convention");
    }

    @Test
    public void unknownSimilarity_throws() {
        assertThrows(IllegalArgumentException.class, () -> VectorSimilarity.score("manhattan", A, B));
    }

    @Test
    public void mismatchedLengths_throws() {
        var shorter = new float[] {1f, 0f};
        assertThrows(IllegalArgumentException.class, () -> VectorSimilarity.score(VectorSimilarity.COSINE, A, shorter));
    }
}
