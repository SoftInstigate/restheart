/*-
 * ========================LICENSE_START=================================
 * restheart-stripe
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.stripe.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.bson.BsonDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockKeeperTest {

    private static BsonDocument order(String lines) {
        return BsonDocument.parse("{ \"line_items\": %s }".formatted(lines));
    }

    @Test
    @DisplayName("quantities are summed per reference")
    void quantities() {
        var quantities = StockKeeper.quantitiesOf(order("""
                [
                  { "product_id": "mug", "quantity": 2 },
                  { "product_id": "tee-classic/yellow-l", "quantity": 1 }
                ]
                """));

        assertEquals(Map.of("mug", 2, "tee-classic/yellow-l", 1), quantities);
    }

    @Test
    @DisplayName("the same reference on two lines comes off the shelf once, summed")
    void sameReferenceTwice() {
        // A cart can hold one line per set of options chosen, and two of them can resolve to the
        // same variant. Decrementing per line would work; summing keeps it to one write.
        var quantities = StockKeeper.quantitiesOf(order("""
                [
                  { "product_id": "mug", "quantity": 2 },
                  { "product_id": "mug", "quantity": 3 }
                ]
                """));

        assertEquals(Map.of("mug", 5), quantities);
    }

    @Test
    @DisplayName("a line missing its product or quantity is skipped, not guessed at")
    void malformedLines() {
        var quantities = StockKeeper.quantitiesOf(order("""
                [
                  { "quantity": 2 },
                  { "product_id": "mug" },
                  { "product_id": "tee", "quantity": "two" },
                  { "product_id": "cap", "quantity": 1 }
                ]
                """));

        assertEquals(Map.of("cap", 1), quantities);
    }

    @Test
    @DisplayName("an order with no lines takes nothing")
    void noLines() {
        assertTrue(StockKeeper.quantitiesOf(BsonDocument.parse("{}")).isEmpty());
        assertTrue(StockKeeper.quantitiesOf(order("[]")).isEmpty());
    }

    @Test
    @DisplayName("a plain product reads its own count")
    void remainingPlain() {
        var after = BsonDocument.parse("{ \"_id\": \"mug\", \"in_stock\": 7 }");
        assertEquals(7, StockKeeper.remainingOf(after, null));
    }

    @Test
    @DisplayName("a variant reads the count of its own entry, not the first one")
    void remainingVariant() {
        var after = BsonDocument.parse("""
                { "_id": "tee-classic",
                  "variants": [ { "id": "blue-m", "in_stock": 4 },
                                { "id": "yellow-l", "in_stock": -1 } ] }
                """);

        assertEquals(-1, StockKeeper.remainingOf(after, "yellow-l"));
    }

    @Test
    @DisplayName("a count that cannot be read is null, so nothing is reported as oversold")
    void unreadableCount() {
        // Null means "no number to keep honest", and take() treats it as untracked. Returning 0
        // here would report every uncounted product as oversold on its first sale.
        assertNull(StockKeeper.remainingOf(BsonDocument.parse("{ \"_id\": \"mug\" }"), null));
        assertNull(StockKeeper.remainingOf(
                BsonDocument.parse("{ \"_id\": \"tee\", \"variants\": [ { \"id\": \"blue-m\" } ] }"), "blue-m"));
        assertNull(StockKeeper.remainingOf(
                BsonDocument.parse("{ \"_id\": \"tee\", \"variants\": [ { \"id\": \"blue-m\", \"in_stock\": 1 } ] }"),
                "yellow-l"));
    }
}
