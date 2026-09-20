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
package org.restheart.security.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.security.MongoPermissions;
import org.restheart.security.analysis.MongoGates.Action;

class MongoGatesTest {

    private static MongoPermissions permissions(String json) {
        return MongoPermissions.from(BsonDocument.parse(json));
    }

    /**
     * The switch is off by default, so a permission whose predicate matches {@code DELETE /coll}
     * still does not permit dropping it — which is the whole reason the analysis cannot stop at the
     * predicate.
     */
    @Test
    void managementIsRefusedUnlessTheSwitchIsOn() {
        var closed = permissions("{ 'mongo': { 'readFilter': null } }");

        assertEquals(Truth.FALSE, MongoGates.allows(closed, Action.managementRequest()));
        assertEquals(Truth.TRUE, MongoGates.allows(closed, Action.ORDINARY));
    }

    @Test
    void managementIsAllowedWhenItIsOn() {
        var open = permissions("{ 'mongo': { 'allowManagementRequests': true } }");

        assertEquals(Truth.TRUE, MongoGates.allows(open, Action.managementRequest()));
    }

    @Test
    void bulkWritesAreRefusedOneByOne() {
        var patchOnly = permissions("{ 'mongo': { 'allowBulkPatch': true } }");

        assertEquals(Truth.TRUE, MongoGates.allows(patchOnly, new Action(false, true, false)));
        assertEquals(Truth.FALSE, MongoGates.allows(patchOnly, new Action(false, false, true)));
    }

    /** No {@code mongo} block at all means nothing to refuse here. */
    @Test
    void aPermissionWithoutTheBlockGatesNothing() {
        assertEquals(Truth.TRUE, MongoGates.allows(null, Action.managementRequest()));
    }
}
