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
package org.restheart.security.authenticators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restheart.security.ApiKeyCredential;

public class MongoApiKeyAuthenticatorTest {

    private MongoApiKeyAuthenticator authenticator;

    @BeforeEach
    void setUp() throws Exception {
        this.authenticator = new MongoApiKeyAuthenticator();
        set("propRoles", "roles");
        set("propExpires", "expiresAt");
        set("propPrincipal", "user");
    }

    private void set(final String field, final Object value) throws Exception {
        final var f = MongoApiKeyAuthenticator.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(this.authenticator, value);
    }

    private static BsonDocument key() {
        return new BsonDocument("user", new BsonString("robot"));
    }

    // ── hashing ──────────────────────────────────────────────────────────────

    @Test
    void sha256MatchesAKnownVector() {
        // echo -n "abc" | shasum -a 256
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            MongoApiKeyAuthenticator.sha256("abc".toCharArray()));
    }

    @Test
    void sha256IsStableAndNarrow() {
        final var a = MongoApiKeyAuthenticator.sha256("rhak_7f2c9a".toCharArray());

        assertEquals(a, MongoApiKeyAuthenticator.sha256("rhak_7f2c9a".toCharArray()));
        assertEquals(64, a.length(), "hex-encoded SHA-256");
        // The prefix is part of what is hashed: two keys differing only there
        // must not collide.
        assertFalse(a.equals(MongoApiKeyAuthenticator.sha256("rhak_7f2c9b".toCharArray())));
    }

    @Test
    void sha256HandlesNonAsciiWithoutMangling() {
        // Encoded as UTF-8 rather than by narrowing chars to bytes, which would
        // silently collapse anything above U+00FF.
        final var a = MongoApiKeyAuthenticator.sha256("kéy".toCharArray());
        final var b = MongoApiKeyAuthenticator.sha256("key".toCharArray());

        assertFalse(a.equals(b));
    }

    // ── roles ────────────────────────────────────────────────────────────────

    @Test
    void rolesComeFromTheKey() {
        final var doc = key().append("roles", new BsonArray(java.util.List.<org.bson.BsonValue>of(
            new BsonString("cli"), new BsonString("reader"))));

        assertEquals(Set.of("cli", "reader"), this.authenticator.rolesOf(doc));
    }

    @Test
    void aKeyWithNoRolesGetsNoRoles() {
        // The security property: never fall back to the user's roles. A key that
        // names none must reach only what is granted to no role at all.
        assertTrue(this.authenticator.rolesOf(key()).isEmpty());
    }

    @Test
    void malformedRolesAreIgnoredRatherThanGuessed() {
        assertTrue(this.authenticator.rolesOf(key().append("roles", new BsonString("cli"))).isEmpty());
        assertEquals(Set.of("cli"), this.authenticator.rolesOf(key().append("roles",
            new BsonArray(java.util.List.<org.bson.BsonValue>of(new BsonString("cli"), new BsonInt32(7))))));
    }

    // ── expiry ───────────────────────────────────────────────────────────────

    @Test
    void aKeyWithNoExpiryNeverExpires() {
        assertFalse(this.authenticator.isExpired(key()));
        assertFalse(this.authenticator.isExpired(key().append("expiresAt", BsonNull.VALUE)));
    }

    @Test
    void expiryIsCheckedInCodeNotOnlyByTheTtlIndex() {
        // A TTL index reclaims lazily, on a background sweep, so a just-expired
        // key can still be present in the collection.
        final var past = new BsonDateTime(System.currentTimeMillis() - 1000);
        final var future = new BsonDateTime(System.currentTimeMillis() + 60_000);

        assertTrue(this.authenticator.isExpired(key().append("expiresAt", past)));
        assertFalse(this.authenticator.isExpired(key().append("expiresAt", future)));
    }

    @Test
    void anUnreadableExpiryIsTreatedAsExpired() {
        // Fail closed: a key whose expiry cannot be read is not a key that
        // should keep working.
        assertTrue(this.authenticator.isExpired(key().append("expiresAt", new BsonBoolean(false))));
        assertTrue(this.authenticator.isExpired(key().append("expiresAt", new BsonString("tomorrow"))));
    }

    // ── the SPI's other verify() forms ───────────────────────────────────────

    @Test
    void verifyByPrincipalIsRefused() {
        // A key is self-identifying. Accepting a principal here would let a
        // caller present a key as somebody else's credential.
        assertNull(this.authenticator.verify("robot", new ApiKeyCredential("rhak_7f2c9a")));
    }

    @Test
    void aCredentialThatIsNotAKeyIsDeclined() {
        assertNull(this.authenticator.verify(new io.undertow.security.idm.PasswordCredential("pwd".toCharArray())));
    }
}
