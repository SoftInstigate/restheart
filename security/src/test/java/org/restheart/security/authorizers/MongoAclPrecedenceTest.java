/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
package org.restheart.security.authorizers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/**
 * The precedence mongoAclAuthorizer applies across roles: {@code {priority: -1, _id: -1}}, the
 * order its query applies within a role, so a tie never depends on the order of the roles (#767).
 */
class MongoAclPrecedenceTest {
    private static MongoAclPermission permission(BsonValue id, String role, int priority) {
        var doc = new BsonDocument("_id", id)
                .append("roles", new org.bson.BsonArray(List.of(new BsonString(role))))
                .append("predicate", new BsonString("path-prefix('/')"))
                .append("priority", new BsonInt32(priority));
        return MongoAclPermission.build(doc);
    }

    private static BsonValue first(MongoAclPermission... permissions) {
        var list = new ArrayList<>(List.of(permissions));
        list.sort(MongoAclAuthorizer.PRECEDENCE);
        return list.get(0).getId();
    }

    @Test
    void theHighestPriorityComesFirst() {
        var low = permission(new BsonString("z"), "author", 100);
        var high = permission(new BsonString("a"), "editor", 200);

        assertEquals(new BsonString("a"), first(low, high));
        assertEquals(new BsonString("a"), first(high, low));
    }

    @Test
    void atATieTheGreatestIdComesFirstWhateverTheOrderOfTheRoles() {
        var older = permission(new BsonObjectId(new ObjectId("0123456789abcdef01234567")), "author", 100);
        var newer = permission(new BsonObjectId(new ObjectId("0123456789abcdef01234568")), "editor", 100);

        assertEquals(newer.getId(), first(older, newer));
        assertEquals(newer.getId(), first(newer, older));
    }

    @Test
    void idsOfDifferentTypesAreOrderedAsMongoDbDoes() {
        // numbers < strings < ObjectId: descending, the ObjectId comes first
        var number = permission(new BsonInt32(5), "a", 100);
        var string = permission(new BsonString("x"), "b", 100);
        var oid = permission(new BsonObjectId(new ObjectId()), "c", 100);

        assertEquals(oid.getId(), first(number, string, oid));
        assertTrue(MongoAclAuthorizer.compareIds(new BsonInt32(5), new BsonString("x")) < 0);
        assertTrue(MongoAclAuthorizer.compareIds(new BsonString("b"), new BsonString("a")) > 0);
    }
}
