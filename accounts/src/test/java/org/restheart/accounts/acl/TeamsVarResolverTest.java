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
package org.restheart.accounts.acl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.restheart.accounts.AccountsService;
import org.restheart.exchange.Request;
import org.restheart.plugins.accounts.Membership;
import org.restheart.plugins.accounts.MembershipProvider;

import io.undertow.security.idm.Account;

/**
 * {@code @teams}: all the caller's teams, by id and by role (#832).
 */
class TeamsVarResolverTest {
    private static final String ALICE = "alice@example.com";
    private static final BsonObjectId A = new BsonObjectId(new ObjectId("0123456789abcdef01234567"));
    private static final BsonObjectId B = new BsonObjectId(new ObjectId("0123456789abcdef01234568"));
    private static final BsonObjectId C = new BsonObjectId(new ObjectId("0123456789abcdef01234569"));

    private static Request<?> request(String userId) {
        var request = mock(Request.class);
        if (userId != null) {
            var principal = mock(Principal.class);
            when(principal.getName()).thenReturn(userId);
            var account = mock(Account.class);
            when(account.getPrincipal()).thenReturn(principal);
            when(request.getAuthenticatedAccount()).thenReturn(account);
        }
        return request;
    }

    private static TeamsVarResolver resolver(String name, MembershipProvider provider) {
        var accountsService = mock(AccountsService.class);
        when(accountsService.getMembershipProvider(any(Request.class))).thenReturn(provider);
        return new TeamsVarResolver(accountsService, name);
    }

    private static MembershipProvider providerWith(Membership... memberships) {
        var provider = mock(MembershipProvider.class);
        when(provider.listMemberships(ALICE)).thenReturn(List.of(memberships));
        return provider;
    }

    /** Alice owns A and C and is a member of B; B is the active one, which @teams ignores. */
    private static TeamsVarResolver threeTeams() {
        return resolver("teams", providerWith(
                new Membership(A, "Acme", null, "owner", false),
                new Membership(B, "Beta", null, "member", true),
                new Membership(C, "Core", null, "owner", false)));
    }

    private static BsonArray array(BsonObjectId... ids) {
        var ret = new BsonArray();
        for (var id : ids) {
            ret.add(id);
        }
        return ret;
    }

    @Test
    void theWholeVariableIsEveryTeamAsATeamClaim() {
        var expected = new BsonArray(List.of(
                new BsonDocument("_id", A).append("role", new BsonString("owner")),
                new BsonDocument("_id", B).append("role", new BsonString("member")),
                new BsonDocument("_id", C).append("role", new BsonString("owner"))));

        assertEquals(expected, threeTeams().resolve(request(ALICE), "@teams"));
    }

    @Test
    void theIdsAreAllTheTeamsWhateverTheRoleAndWhicheverIsActive() {
        assertEquals(array(A, B, C), threeTeams().resolve(request(ALICE), "@teams._id"));
    }

    @Test
    void aRoleGivesTheTeamsWhereTheCallerHasIt() {
        assertEquals(array(A, C), threeTeams().resolve(request(ALICE), "@teams.owner"));
        assertEquals(array(B), threeTeams().resolve(request(ALICE), "@teams.member"));
    }

    @Test
    void aRoleNobodyHasGivesAnEmptyArray() {
        assertEquals(new BsonArray(), threeTeams().resolve(request(ALICE), "@teams.admin"));
    }

    @Test
    void anExpressionDeeperThanOneSegmentIsUnresolved() {
        assertEquals(BsonNull.VALUE, threeTeams().resolve(request(ALICE), "@teams.owner._id"));
        assertEquals(BsonNull.VALUE, threeTeams().resolve(request(ALICE), "@teams."));
    }

    @Test
    void theVariableIsCalledLikeTheClaimWithAnS() {
        var resolver = resolver("orgs", providerWith(new Membership(A, "Acme", null, "owner", true)));

        assertEquals("orgs", resolver.name());
        assertEquals(array(A), resolver.resolve(request(ALICE), "@orgs.owner"));
        assertEquals(BsonNull.VALUE, resolver.resolve(request(ALICE), "@teams.owner"));
    }

    @Test
    void aUserInNoTeamHasAnEmptyArray() {
        var resolver = resolver("teams", providerWith());

        assertEquals(new BsonArray(), resolver.resolve(request(ALICE), "@teams"));
        assertEquals(new BsonArray(), resolver.resolve(request(ALICE), "@teams._id"));
    }

    @Test
    void anAnonymousCallerHasAnEmptyArray() {
        assertEquals(new BsonArray(), threeTeams().resolve(request(null), "@teams._id"));
    }

    @Test
    void aFailingProviderDeniesWithAnEmptyArray() {
        var provider = mock(MembershipProvider.class);
        when(provider.listMemberships(any())).thenThrow(new IllegalStateException("database away"));

        assertEquals(new BsonArray(), resolver("teams", provider).resolve(request(ALICE), "@teams.owner"));
    }
}
