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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.restheart.exchange.Request;
import org.restheart.security.ApiKeyCredential;
import org.restheart.security.MongoRealmAccount;
import org.restheart.security.tokens.JwtTokenManager;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

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

    // ── the account ──────────────────────────────────────────────────────────

    @Test
    void theAccountCarriesThePrincipalInItsProperties() throws Exception {
        // Not decoration. An ACL predicate writing equals(@user._id, ${userId}),
        // a GraphQL mapping matching $arg: "@user._id" and an aggregation
        // interpolating @user all read identity from the properties, never from
        // the principal name. With an empty document they resolve to null, a
        // query matching on null matches nothing, and the caller authenticates,
        // is authorised, gets a 200 and sees no data.
        final var account = this.authenticator.accountOf("restheart", key());

        assertEquals("robot", account.getPrincipal().getName());
        assertEquals(new BsonString("robot"), account.properties().get("_id"));
    }

    @Test
    void theAccountCarriesNothingBeyondIdentityTenantAndTheApiKeyMarker() throws Exception {
        // The key document holds the hash, and is the tenant's to shape — what
        // else is in it was not written with an ACL predicate in mind, so `_id`
        // is all of it that reaches the account.
        //
        // The two additions do not come from the document. `apiKey` is stamped
        // so JwtTokenManager can refuse to renew a token minted from a key
        // (#729). `authDb` is the database the key was found in, so the token
        // names its tenant as one from a password login does (#738).
        final var account = this.authenticator.accountOf("tenant_a", key()
                .append("hash", new BsonString("d41d8cd9"))
                .append("name", new BsonString("CI deploy"))
                .append("roles", new BsonArray(java.util.List.of(new BsonString("cli")))));

        assertEquals(Set.of("_id", "authDb", JwtTokenManager.FROM_API_KEY), account.properties().keySet());
        assertEquals(new BsonString("tenant_a"), account.properties().get("authDb"));
        assertEquals(Set.of("cli"), account.getRoles());
    }

    @Test
    void theIdentityKeyIsIdNotThePrincipalPropertyName() throws Exception {
        // prop-principal says where to read the principal from in this
        // collection. What to call it once it is on the account is a different
        // question, and the answer is what consumers write: _id.
        set("propPrincipal", "owner");

        final var account = this.authenticator.accountOf("restheart",
                new BsonDocument("owner", new BsonString("robot")));

        assertEquals(new BsonString("robot"), account.properties().get("_id"));
        assertNull(account.properties().get("owner"));
    }

    @Test
    void aKeyThatNamesNoPrincipalYieldsNoAccount() throws Exception {
        assertNull(this.authenticator.accountOf("restheart", new BsonDocument()));
        assertNull(this.authenticator.accountOf("restheart", new BsonDocument("user", BsonNull.VALUE)));
        assertNull(this.authenticator.accountOf("restheart", new BsonDocument("user", new BsonString("  "))));
        assertNull(this.authenticator.accountOf("restheart", new BsonDocument("user", new BsonInt32(7))));
    }

    // ── the database, per request (#738) ─────────────────────────────────────

    private static final String KEY = "rhak_7f2c9a";

    /** A keys collection holding {@code doc}, or nothing when it is null. */
    @SuppressWarnings("unchecked")
    private static MongoCollection<BsonDocument> keysIn(final MongoClient mclient, final String db, final BsonDocument doc) {
        final var database = mock(MongoDatabase.class);
        final MongoCollection<Document> coll = mock(MongoCollection.class);
        final MongoCollection<BsonDocument> keys = mock(MongoCollection.class);
        final FindIterable<BsonDocument> found = mock(FindIterable.class);

        when(mclient.getDatabase(db)).thenReturn(database);
        when(database.getCollection("apiKeys")).thenReturn(coll);
        when(coll.withDocumentClass(BsonDocument.class)).thenReturn(keys);
        when(keys.find(any(Bson.class))).thenReturn(found);
        when(found.first()).thenReturn(doc);

        return keys;
    }

    private static Request<?> requestFor(final String keysDb) {
        final Request<?> req = mock(Request.class);
        when(req.<String>attachedParam(MongoApiKeyAuthenticator.OVERRIDE_KEYS_DB)).thenReturn(keysDb);
        return req;
    }

    /** Initialised as the plugin would be with an empty configuration: keys cache on. */
    private MongoClient initialised() throws Exception {
        final var mclient = mock(MongoClient.class);
        set("config", Map.of());
        this.authenticator.init();
        set("mclient", mclient);
        return mclient;
    }

    @Test
    void aKeyWorksOnlyOnTheTenantItsDatabaseBelongsTo() throws Exception {
        final var mclient = initialised();
        keysIn(mclient, "tenant_a", key().append("roles", new BsonArray(java.util.List.of(new BsonString("cli")))));
        keysIn(mclient, "tenant_b", null);

        final var account = this.authenticator.verify(requestFor("tenant_a"), new ApiKeyCredential(KEY));

        assertNotNull(account);
        assertEquals(new BsonString("tenant_a"), ((MongoRealmAccount) account).properties().get("authDb"));

        // Asked second, and with the cache on, on purpose: keyed on the hash
        // alone, tenant_a's verification would be served for tenant_b.
        assertNull(this.authenticator.verify(requestFor("tenant_b"), new ApiKeyCredential(KEY)));
    }

    @Test
    void aKeyWorksOnlyOnItsTenantAlsoWithTheCacheOff() throws Exception {
        // The same property without the cache, so that it is the lookup itself
        // being tested and not only the cache key.
        final var mclient = mock(MongoClient.class);
        set("config", Map.of("cache-enabled", false));
        this.authenticator.init();
        set("mclient", mclient);

        keysIn(mclient, "tenant_a", key());
        keysIn(mclient, "tenant_b", null);

        assertNull(this.authenticator.verify(requestFor("tenant_b"), new ApiKeyCredential(KEY)));
        assertNotNull(this.authenticator.verify(requestFor("tenant_a"), new ApiKeyCredential(KEY)));
        assertNull(this.authenticator.verify(requestFor("tenant_b"), new ApiKeyCredential(KEY)));
    }

    @Test
    void theSameKeyInTwoTenantsYieldsEachTenantsOwnAccount() throws Exception {
        // The worst case for a cache keyed on the hash alone: the key exists in
        // both databases, so a leak would not be a refusal turning into an
        // acceptance but one tenant's roles and principal handed to the other —
        // and nothing would look wrong.
        final var mclient = initialised();
        keysIn(mclient, "tenant_a", new BsonDocument("user", new BsonString("alice"))
                .append("roles", new BsonArray(java.util.List.of(new BsonString("admin")))));
        keysIn(mclient, "tenant_b", new BsonDocument("user", new BsonString("bob"))
                .append("roles", new BsonArray(java.util.List.of(new BsonString("reader")))));

        for (int i = 0;i < 2;i++) {
            final var a = (MongoRealmAccount) this.authenticator.verify(requestFor("tenant_a"), new ApiKeyCredential(KEY));
            final var b = (MongoRealmAccount) this.authenticator.verify(requestFor("tenant_b"), new ApiKeyCredential(KEY));

            assertEquals("alice", a.getPrincipal().getName());
            assertEquals(Set.of("admin"), a.getRoles());
            assertEquals(new BsonString("tenant_a"), a.properties().get("authDb"));

            assertEquals("bob", b.getPrincipal().getName());
            assertEquals(Set.of("reader"), b.getRoles());
            assertEquals(new BsonString("tenant_b"), b.properties().get("authDb"));
        }
    }

    @Test
    void anUnusableOverrideIsRefusedNeverReadAsAbsent() throws Exception {
        // Fail closed. A resolver that attached the param but failed to name a
        // tenant must not fall back to keys-db: that would accept the default
        // tenant's keys on a request meant for some other tenant.
        final var mclient = initialised();
        keysIn(mclient, "restheart", key());

        assertNull(this.authenticator.verify(requestFor(""), new ApiKeyCredential(KEY)));
        assertNull(this.authenticator.verify(requestFor("   "), new ApiKeyCredential(KEY)));

        final Request<?> notAString = mock(Request.class);
        when(notAString.<Object>attachedParam(MongoApiKeyAuthenticator.OVERRIDE_KEYS_DB)).thenReturn(7);
        assertNull(this.authenticator.verify(notAString, new ApiKeyCredential(KEY)));

        Mockito.verify(mclient, never()).getDatabase(any());
    }

    @Test
    void aDatabaseNameMongoRejectsIsARefusalNotAnError() throws Exception {
        final var mclient = initialised();
        when(mclient.getDatabase("bad.name")).thenThrow(new IllegalArgumentException("invalid database name"));

        assertNull(this.authenticator.verify(requestFor("bad.name"), new ApiKeyCredential(KEY)));
    }

    @Test
    void withoutTheOverrideTheConfiguredDatabaseIsUsed() throws Exception {
        final var mclient = initialised();
        keysIn(mclient, "restheart", key());

        assertNotNull(this.authenticator.verify(requestFor(null), new ApiKeyCredential(KEY)));
        assertNotNull(this.authenticator.verify(new ApiKeyCredential(KEY)));
        assertEquals("restheart", this.authenticator.getKeysDb(requestFor(null)));
    }

    @Test
    void lastUsedAtIsWrittenWhereTheKeyWasFound() throws Exception {
        final var mclient = initialised();
        final var tenantKeys = keysIn(mclient, "tenant_a", key());
        final var defaultKeys = keysIn(mclient, "restheart", null);

        assertNotNull(this.authenticator.verify(requestFor("tenant_a"), new ApiKeyCredential(KEY)));

        Mockito.verify(tenantKeys).updateOne(any(Bson.class), any(Bson.class));
        Mockito.verify(defaultKeys, never()).updateOne(any(Bson.class), any(Bson.class));
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
