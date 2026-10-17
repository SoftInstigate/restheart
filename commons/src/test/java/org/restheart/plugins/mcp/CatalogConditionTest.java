/*-
 * ========================LICENSE_START=================================
 * restheart-commons
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
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

class CatalogConditionTest {

    private static BsonDocument mcp(String json) {
        return BsonDocument.parse(json);
    }

    @Test
    void anAbsentBlockDeclaresNothing() {
        assertNull(CatalogCondition.showIf(mcp("{ 'description': 'x' }")));
        assertEquals(Set.of(), CatalogCondition.hideFromRoles(mcp("{ 'description': 'x' }")));
        assertNull(CatalogCondition.showIf(null));
    }

    @Test
    void theRolesAreRead() {
        assertEquals(Set.of("employee", "intern"),
                CatalogCondition.hideFromRoles(mcp("{ 'hide_from_roles': ['employee', 'intern'] }")));
    }

    @Test
    void aConditionOnTheSessionIsAccepted() {
        assertTrue(CatalogCondition.problemsWith("equals(@user.plan, 'gold')").isEmpty());
        assertTrue(CatalogCondition.problemsWith("path-prefix('/orders')").isEmpty());
    }

    /**
     * The datum does not exist when a listing is composed, so the condition has no meaning there —
     * an authoring mistake, not a case to resolve by showing.
     */
    @Test
    void aConditionOnTheCallIsRefused() {
        assertFalse(CatalogCondition.problemsWith("qparams-contain(token)").isEmpty());
        assertFalse(CatalogCondition.problemsWith("equals(%{q,token}, 'x')").isEmpty());
        assertFalse(CatalogCondition.problemsWith("equals(@qparams['token'], 'x')").isEmpty());
    }

    @Test
    void aPredicateNobodyDeclaresIsRefusedToo() {
        assertFalse(CatalogCondition.problemsWith("is-gold-customer()").isEmpty());
    }

    @Test
    void nonsenseIsRefusedWithItsReason() {
        var problems = CatalogCondition.problemsWith("path('/orders'");

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("not a valid predicate"), problems.toString());
    }

    /** One bad atom in an otherwise fine condition is enough, and it is named. */
    @Test
    void theOffendingAtomIsNamed() {
        var problems = CatalogCondition.problemsWith("equals(@user.plan, 'gold') and qparams-contain(token)");

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("qparams-contain"), problems.toString());
    }
}
