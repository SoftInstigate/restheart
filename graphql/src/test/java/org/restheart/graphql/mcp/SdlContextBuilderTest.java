/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.graphql.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class SdlContextBuilderTest {

    private static final String SDL = """
            type Query {
                inventoryBySku(sku: String!, limit: Int): [Item]
            }
            type Mutation {
                createOrder(sku: String!, quantity: Int!): Order
            }
            type Item { sku: String, qty: Int }
            type Order { id: ID, sku: String, quantity: Int }
            """;

    @Test
    public void nullSdl_emptyOperations() {
        assertTrue(SdlContextBuilder.queries(null).isEmpty());
    }

    @Test
    public void blankSdl_emptyOperations() {
        assertTrue(SdlContextBuilder.queries("   ").isEmpty());
    }

    @Test
    public void malformedSdl_emptyOperationsNoThrow() {
        assertTrue(SdlContextBuilder.queries("type Query { this is not valid SDL @#$").isEmpty());
    }

    @Test
    public void noQueryType_emptyOperations() {
        assertTrue(SdlContextBuilder.queries("type Item { sku: String }").isEmpty());
    }

    @Test
    public void queries_parsedWithNameArgsAndReturnType() {
        var queries = SdlContextBuilder.queries(SDL);

        assertEquals(1, queries.size());
        var op = queries.get(0);
        assertEquals("inventoryBySku", op.name());
        assertEquals("[Item]", op.returnType());
        assertEquals(2, op.args().size());
    }

    @Test
    public void requiredArg_hasNonNullTypeAndRequiredTrue() {
        var op = SdlContextBuilder.queries(SDL).get(0);
        var sku = op.args().stream().filter(a -> a.name().equals("sku")).findFirst().orElseThrow();

        assertEquals("String!", sku.type());
        assertTrue(sku.required());
    }

    @Test
    public void optionalArg_noBangAndRequiredFalse() {
        var op = SdlContextBuilder.queries(SDL).get(0);
        var limit = op.args().stream().filter(a -> a.name().equals("limit")).findFirst().orElseThrow();

        assertEquals("Int", limit.type());
        assertEquals(false, limit.required());
    }

    @Test
    public void mutationTypeInSdl_neverSurfacedByQueries() {
        // RESTHeart's GraphQL API is read-only and has no mutation execution path; even when an
        // app's SDL declares a Mutation type, queries() must not surface its fields as if they
        // were callable — only Query fields are ever returned
        var queries = SdlContextBuilder.queries(SDL);

        assertTrue(queries.stream().noneMatch(op -> op.name().equals("createOrder")));
    }

    @Test
    public void bsonScalarInSdl_parsesWithoutError() {
        var sdlWithBsonScalar = """
                type Query {
                    findById(id: BsonObjectId!): Item
                }
                type Item { sku: String }
                """;

        var queries = SdlContextBuilder.queries(sdlWithBsonScalar);

        assertEquals(1, queries.size());
        assertEquals("BsonObjectId!", queries.get(0).args().get(0).type());
    }
}
