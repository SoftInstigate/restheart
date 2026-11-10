/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.MongoRequest;

/**
 * The order a permission's {@code mongo.sort} gives a read (since 9.16).
 */
public class RequestFiltersSortTest {

    private static MongoRequest requestWith(Map<String, String> qparams) {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        var queryParams = new HashMap<String, Deque<String>>();
        qparams.forEach((k, v) -> {
            var d = new ArrayDeque<String>();
            d.add(v);
            queryParams.put(k, d);
        });
        when(request.getExchange()).thenReturn(exchange);
        when(exchange.getQueryParameters()).thenReturn(queryParams);
        return request;
    }

    private static BsonDocument sort(String json, Map<String, String> qparams) {
        return RequestFilters.resolveSort(requestWith(qparams), BsonDocument.parse(json));
    }

    @Test
    public void aFixedOrderGetsIdAppended() {
        assertEquals(BsonDocument.parse("{\"name\": 1, \"_id\": 1}"), sort("{\"name\": 1}", Map.of()));
        assertEquals(BsonDocument.parse("{\"_id\": -1}"), sort("{\"_id\": -1}", Map.of()));
    }

    @Test
    public void theClientPicksOneOfTheDeclaredOrders() {
        var orders = """
                {"$ifvar": ["@qparams['order']",
                  {"by-name": {"name": 1}, "newest": {"createdAt": -1}},
                  {"createdAt": -1}]}""";

        assertEquals(BsonDocument.parse("{\"name\": 1, \"_id\": 1}"), sort(orders, Map.of("order", "by-name")));
        assertEquals(BsonDocument.parse("{\"createdAt\": -1, \"_id\": 1}"), sort(orders, Map.of()));

        var e = assertThrows(IllegalArgumentException.class, () -> sort(orders, Map.of("order", "price")));
        assertTrue(e.getMessage().contains("by-name"), e.getMessage());
    }

    @Test
    public void withoutAnElseAndAChoiceThereIsNoOrder() {
        assertNull(sort("{\"$ifvar\": [\"@qparams['order']\", {\"by-name\": {\"name\": 1}}]}", Map.of()));
    }

    @Test
    public void theDirectionMayComeFromTheRequestWithADefault() {
        var clause = "{\"createdAt\": {\"$var\": [\"@qparams['dir']\", -1]}}";

        assertEquals(BsonDocument.parse("{\"createdAt\": -1, \"_id\": 1}"), sort(clause, Map.of()));
        assertEquals(BsonDocument.parse("{\"createdAt\": 1, \"_id\": 1}"), sort(clause, Map.of("dir", "1")));
    }

    @Test
    public void aDirectionThatIsNotOneOrMinusOneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> sort("{\"createdAt\": \"@qparams['dir']\"}", Map.of("dir", "price")));
        assertThrows(IllegalArgumentException.class, () -> sort("{\"createdAt\": \"@qparams['dir']\"}", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> sort("{\"createdAt\": 2}", Map.of()));
    }
}
