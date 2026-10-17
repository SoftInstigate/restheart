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
import java.util.Optional;

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
 * {@code @team} answers the active membership as the accounts endpoints put it in a token,
 * {@code { _id, role }}, whatever the provider stores: the resolver asks the provider, never the
 * document or the token (#765).
 */
class TeamVarResolverTest {
    private static final BsonObjectId TEAM_ID = new BsonObjectId(new ObjectId("0123456789abcdef01234567"));

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

    private static TeamVarResolver resolver(String name, MembershipProvider provider) {
        var accountsService = mock(AccountsService.class);
        when(accountsService.getMembershipProvider(any(Request.class))).thenReturn(provider);
        return new TeamVarResolver(accountsService, name);
    }

    /** A provider that stores the active team however it likes: only activeMembership matters here. */
    private static MembershipProvider providerWith(Membership membership) {
        var provider = mock(MembershipProvider.class);
        when(provider.activeMembership("alice@example.com")).thenReturn(Optional.ofNullable(membership));
        return provider;
    }

    @Test
    void theWholeVariableIsTheTeamClaimDocument() {
        var resolver = resolver("team", providerWith(new Membership(TEAM_ID, "Acme", null, "owner", true)));

        assertEquals(new BsonDocument("_id", TEAM_ID).append("role", new BsonString("owner")),
                resolver.resolve(request("alice@example.com"), "@team"));
    }

    @Test
    void theSubPropertiesAreTheIdAndTheRole() {
        var resolver = resolver("team", providerWith(new Membership(TEAM_ID, "Acme", null, "owner", true)));

        assertEquals(TEAM_ID, resolver.resolve(request("alice@example.com"), "@team._id"));
        assertEquals(new BsonString("owner"), resolver.resolve(request("alice@example.com"), "@team.role"));
        assertEquals(BsonNull.VALUE, resolver.resolve(request("alice@example.com"), "@team.displayName"));
    }

    @Test
    void theVariableIsCalledLikeTheClaim() {
        var resolver = resolver("org", providerWith(new Membership(TEAM_ID, "Acme", null, "member", true)));

        assertEquals("org", resolver.name());
        assertEquals(new BsonString("member"), resolver.resolve(request("alice@example.com"), "@org.role"));
        assertEquals(BsonNull.VALUE, resolver.resolve(request("alice@example.com"), "@team.role"));
    }

    @Test
    void anAnonymousCallerHasNoTeam() {
        var resolver = resolver("team", providerWith(new Membership(TEAM_ID, "Acme", null, "owner", true)));

        assertEquals(BsonNull.VALUE, resolver.resolve(request(null), "@team"));
    }

    @Test
    void aCallerWithoutAnActiveMembershipHasNoTeam() {
        var resolver = resolver("team", providerWith(null));

        assertEquals(BsonNull.VALUE, resolver.resolve(request("alice@example.com"), "@team._id"));
    }

    @Test
    void aFailingProviderDeniesRatherThanGrants() {
        var provider = mock(MembershipProvider.class);
        when(provider.activeMembership(any())).thenThrow(new IllegalStateException("database away"));

        assertEquals(BsonNull.VALUE, resolver("team", provider).resolve(request("alice@example.com"), "@team"));
    }
}
