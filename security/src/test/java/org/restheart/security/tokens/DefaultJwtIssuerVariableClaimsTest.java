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
package org.restheart.security.tokens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;
import org.restheart.security.AclVarsRegistryProvider;
import org.restheart.security.VarResolver;

import com.auth0.jwt.JWT;

/**
 * The {@code @} entries of {@code account-properties-claims}: an ACL variable resolved with the
 * issuing request, put in the token under its name (#765).
 */
class DefaultJwtIssuerVariableClaimsTest {
    private static final String HEX = "0123456789abcdef01234567";
    private static final BsonDocument TEAM = new BsonDocument("_id", new BsonObjectId(new ObjectId(HEX)))
            .append("role", new BsonString("owner"));

    /** A resolver answering a fixed value; not cacheable, so the test needs no request attachments. */
    private record Fixed(String name, BsonValue value) implements VarResolver {
        @Override
        public BsonValue resolve(Request<?> request, String var) {
            return value;
        }

        @Override
        public boolean cacheable() {
            return false;
        }
    }

    @BeforeAll
    static void registerResolvers() {
        var registry = new AclVarsRegistryProvider().get(null);
        registry.register(new Fixed("claimvar", TEAM));
        registry.register(new Fixed("claimnothing", BsonNull.VALUE));
        // a resolver may well be called like a denylisted claim: the denylist still wins
        registry.register(new Fixed("apiKey", new BsonString("leak")));
    }

    private static DefaultJwtIssuer issuer(List<String> claims) {
        return new DefaultJwtIssuer(DefaultJwtIssuer.algorithm("HS256", "a-key-long-enough-for-the-test-0123456789"),
                "test", null, claims, null);
    }

    @Test
    void aVariableEntryBecomesAClaimUnderTheVariableName() {
        var claims = issuer(List.of("@claimvar")).variableClaims(mock(Request.class), null);

        assertEquals(Map.of("claimvar", Map.of("_id", Map.of("$oid", HEX), "role", "owner")), claims);
    }

    @Test
    void theClaimIsInTheIssuedToken() {
        var token = issuer(List.of("plain", "@claimvar")).issue("alice", Set.of("user"),
                new Date(System.currentTimeMillis() + 60_000), Map.<String, Object>of("plain", "copied"), null, null, null,
                mock(Request.class));

        var decoded = JWT.decode(token);
        assertEquals("copied", decoded.getClaim("plain").asString(), "a plain entry is copied as before");
        assertEquals(Map.of("_id", Map.of("$oid", HEX), "role", "owner"), decoded.getClaim("claimvar").asMap());
    }

    @Test
    void aVariableResolvingToNothingAddsNoClaim() {
        assertTrue(issuer(List.of("@claimnothing")).variableClaims(mock(Request.class), null).isEmpty());
    }

    @Test
    void anUnregisteredVariableAddsNoClaim() {
        assertTrue(issuer(List.of("@nobodyknows")).variableClaims(mock(Request.class), null).isEmpty());
    }

    @Test
    void aDenylistedNameIsRefusedWhateverResolvesIt() {
        assertTrue(issuer(List.of("@apiKey")).variableClaims(mock(Request.class), null).isEmpty());
    }

    @Test
    void withoutARequestThereIsNothingToResolveAgainst() {
        assertTrue(issuer(List.of("@claimvar")).variableClaims(null, null).isEmpty());
    }

    @Test
    void theOverrideListIsReadLikeTheConfiguredOne() {
        var claims = issuer(List.of("plain")).variableClaims(mock(Request.class), List.of("@claimvar"));

        assertEquals(Set.of("claimvar"), claims.keySet());
    }

    @Test
    void aVariableEntryIsNeverLookedUpInTheProperties() {
        // a property that happens to be called like the entry is not a claim
        var claims = issuer(List.of("@claimvar", "plain")).accountClaims(Map.<String, Object>of("@claimvar", "stored", "plain", "copied"), null);

        assertEquals(Map.of("plain", "copied"), claims);
        assertFalse(claims.containsKey("@claimvar"));
    }

    @Test
    void theVariableNameIsTheEntryWithoutTheAt() {
        assertEquals("team", DefaultJwtIssuer.variableClaimName("@team"));
        assertEquals("team", DefaultJwtIssuer.variableClaimName("@team.role"));
        assertTrue(DefaultJwtIssuer.isVariableClaim("@team"));
        assertFalse(DefaultJwtIssuer.isVariableClaim("team"));
        assertFalse(DefaultJwtIssuer.isVariableClaim("@"));
    }
}
