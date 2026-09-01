package org.restheart.accounts.util;

import com.auth0.jwt.JWT;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.security.Authenticator;
import org.restheart.security.authenticators.MongoRealmAuthenticator;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link JwtHelper} never copies denylisted user-document fields into a JWT
 * claim (issue #661), regardless of whether the claim list comes from the constructor
 * (static {@code accountsConfig.account-properties-claims}) or the per-call override
 * (per-tenant {@code override-accounts-account-properties-claims}).
 */
class JwtHelperTest {

    private static final String KEY = "test-signing-key-0123456789";
    private static final String ISSUER = "test-issuer";
    private static final Set<String> ROLES = Set.of("user");

    @Test
    void defaultPasswordPropertyIsDenylistedEvenWhenExplicitlyRequested() {
        var userDoc = new BsonDocument()
                .append("password", new BsonString("$2a$hash"))
                .append("emailVerificationToken", new BsonString("tok1"))
                .append("emailVerificationCreatedAt", new BsonString("2026-01-01"))
                .append("passwordResetToken", new BsonString("tok2"))
                .append("passwordResetCreatedAt", new BsonString("2026-01-02"))
                .append("plan", new BsonString("pro"));

        var claims = List.of("password", "emailVerificationToken", "emailVerificationCreatedAt",
                "passwordResetToken", "passwordResetCreatedAt", "plan");

        var jwt = new JwtHelper(KEY, ISSUER, 15, claims);
        var token = jwt.issueToken("user@example.com", ROLES, null, null, null, userDoc);
        var decoded = JWT.decode(token);

        assertNull(decoded.getClaim("password").asString(), "password must never become a claim");
        assertNull(decoded.getClaim("emailVerificationToken").asString());
        assertNull(decoded.getClaim("emailVerificationCreatedAt").asString());
        assertNull(decoded.getClaim("passwordResetToken").asString());
        assertNull(decoded.getClaim("passwordResetCreatedAt").asString());
        assertEquals("pro", decoded.getClaim("plan").asString());
    }

    @Test
    void configuredPasswordPropertyNameIsResolvedFromMongoRealmAuthenticator() {
        var mra = mock(MongoRealmAuthenticator.class);
        when(mra.getPropPassword()).thenReturn("pwd");

        @SuppressWarnings("unchecked")
        var pluginRecord = (PluginRecord<Authenticator>) mock(PluginRecord.class);
        when(pluginRecord.isEnabled()).thenReturn(true);
        when(pluginRecord.getInstance()).thenReturn(mra);

        var registry = mock(PluginsRegistry.class);
        when(registry.getAuthenticator("mongoRealmAuthenticator")).thenReturn(pluginRecord);

        var userDoc = new BsonDocument()
                .append("pwd", new BsonString("$2a$hash"))
                .append("plan", new BsonString("pro"));

        var jwt = new JwtHelper(KEY, ISSUER, 15, List.of("pwd", "plan"), registry);
        var token = jwt.issueToken("user@example.com", ROLES, null, null, null, userDoc);
        var decoded = JWT.decode(token);

        assertNull(decoded.getClaim("pwd").asString(),
                "the configured password property must be denylisted regardless of its name");
        assertEquals("pro", decoded.getClaim("plan").asString());
    }

    @Test
    void perCallOverrideReplacesConstructorClaimsList() {
        var userDoc = new BsonDocument()
                .append("a", new BsonString("from-constructor-list"))
                .append("b", new BsonString("from-override-list"));

        var jwt = new JwtHelper(KEY, ISSUER, 15, List.of("a"), null);

        // No override: constructor list ("a") applies.
        var tokenDefault = jwt.issueToken("user@example.com", ROLES, null, null, null, userDoc, null);
        var decodedDefault = JWT.decode(tokenDefault);
        assertEquals("from-constructor-list", decodedDefault.getClaim("a").asString());
        assertNull(decodedDefault.getClaim("b").asString());

        // Override present: only "b" applies, "a" is not copied even though the
        // constructor list still contains it.
        var tokenOverridden = jwt.issueToken("user@example.com", ROLES, null, null, null, userDoc, List.of("b"));
        var decodedOverridden = JWT.decode(tokenOverridden);
        assertNull(decodedOverridden.getClaim("a").asString());
        assertEquals("from-override-list", decodedOverridden.getClaim("b").asString());
    }

    @Test
    void denylistAppliesToOverrideListToo() {
        var userDoc = new BsonDocument()
                .append("password", new BsonString("$2a$hash"))
                .append("plan", new BsonString("pro"));

        var jwt = new JwtHelper(KEY, ISSUER, 15, List.of("plan"), null);

        // A tenant-supplied override that lists "password" must not leak it.
        var token = jwt.issueToken("user@example.com", ROLES, null, null, null, userDoc,
                List.of("password", "plan"));
        var decoded = JWT.decode(token);

        assertNull(decoded.getClaim("password").asString());
        assertEquals("pro", decoded.getClaim("plan").asString());
    }
}
