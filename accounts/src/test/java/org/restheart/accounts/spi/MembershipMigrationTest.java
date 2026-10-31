/*-
 * ========================LICENSE_START=================================
 * restheart-accounts
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
package org.restheart.accounts.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/** The migration planned on documents in the pre-9.12.0 shape (#766). */
class MembershipMigrationTest {
    private static final BsonObjectId ACME = new BsonObjectId(new ObjectId("0123456789abcdef01234567"));
    private static final BsonObjectId GLOBEX = new BsonObjectId(new ObjectId("0123456789abcdef01234568"));
    private static final BsonObjectId GONE = new BsonObjectId(new ObjectId("0123456789abcdef01234569"));

    private static BsonDocument team(BsonObjectId id, String... membersAndRoles) {
        var doc = BsonDocument.parse("{\"name\": \"t\", \"members\": []}").append("_id", id);
        for (var i = 0; i < membersAndRoles.length; i += 2) {
            doc.getArray("members").add(BsonDocument.parse("{\"userId\": \"" + membersAndRoles[i] + "\", \"role\": \"" + membersAndRoles[i + 1] + "\"}"));
        }
        return doc;
    }

    private static final Map<BsonValue, BsonDocument> TEAMS = Map.of(
            ACME, team(ACME, "alice@example.com", "owner", "bob@example.com", "member"),
            GLOBEX, team(GLOBEX, "bob@example.com", "owner"));

    private static Optional<BsonDocument> teamById(BsonValue id) {
        return Optional.ofNullable(TEAMS.get(id));
    }

    @Test
    void theOldFieldsAreUnsetAndAConfirmedMembershipIsNotReported() {
        var alice = BsonDocument.parse("{\"_id\": \"alice@example.com\", \"teams\": [{\"role\": \"owner\"}]}");
        alice.getArray("teams").get(0).asDocument().append("id", ACME);
        alice.append("team", new BsonDocument("_id", ACME).append("role", new org.bson.BsonString("owner")));

        var plan = MembershipMigration.plan(List.of(alice), MembershipMigrationTest::teamById);

        assertEquals(1, plan.usersScanned());
        assertTrue(plan.mismatches().isEmpty(), "alice is an owner of acme, as her document says");
        assertEquals(1, plan.updates().size());
        var update = plan.updates().get(0);
        assertEquals("alice@example.com", update.userId());
        assertEquals(BsonDocument.parse("{\"$unset\": {\"teams\": \"\", \"team.role\": \"\"}}"), update.update());
    }

    @Test
    void aMembershipTheTeamDoesNotConfirmIsReportedNotAdded() {
        var carol = BsonDocument.parse("{\"_id\": \"carol@example.com\", \"teams\": []}");
        carol.getArray("teams").add(new BsonDocument("id", ACME).append("role", new org.bson.BsonString("member")));
        carol.getArray("teams").add(new BsonDocument("id", GONE).append("role", new org.bson.BsonString("member")));

        var plan = MembershipMigration.plan(List.of(carol), MembershipMigrationTest::teamById);

        assertEquals(2, plan.mismatches().size());
        assertTrue(plan.mismatches().get(0).why().contains("members do not include"), plan.mismatches().get(0).toString());
        assertTrue(plan.mismatches().get(1).why().contains("does not exist"), plan.mismatches().get(1).toString());
        // the document is cleaned all the same: the team decides, and it says no
        assertEquals(BsonDocument.parse("{\"$unset\": {\"teams\": \"\"}}"), plan.updates().get(0).update());
    }

    @Test
    void aRoleTheTeamDisagreesWithIsReported() {
        var bob = BsonDocument.parse("{\"_id\": \"bob@example.com\", \"teams\": []}");
        bob.getArray("teams").add(new BsonDocument("id", ACME).append("role", new org.bson.BsonString("owner")));

        var plan = MembershipMigration.plan(List.of(bob), MembershipMigrationTest::teamById);

        assertEquals(1, plan.mismatches().size());
        assertTrue(plan.mismatches().get(0).why().contains("say member"), plan.mismatches().get(0).toString());
    }

    @Test
    void aBareActiveTeamIdBecomesADocument() {
        var dave = new BsonDocument("_id", new org.bson.BsonString("dave@example.com")).append("team", GLOBEX);

        var plan = MembershipMigration.plan(List.of(dave), MembershipMigrationTest::teamById);

        assertEquals(1, plan.updates().size());
        assertEquals(new BsonDocument("$set", new BsonDocument("team", new BsonDocument("_id", GLOBEX))), plan.updates().get(0).update());
    }

    @Test
    void aDocumentAlreadyInTheNewShapeIsLeftAlone() {
        var erin = new BsonDocument("_id", new org.bson.BsonString("erin@example.com")).append("team", new BsonDocument("_id", ACME));

        var plan = MembershipMigration.plan(List.of(erin), MembershipMigrationTest::teamById);

        assertEquals(1, plan.usersScanned());
        assertTrue(plan.updates().isEmpty());
        assertTrue(plan.mismatches().isEmpty());
    }
}
