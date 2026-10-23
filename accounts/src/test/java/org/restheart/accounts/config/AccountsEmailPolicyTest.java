package org.restheart.accounts.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;

class AccountsEmailPolicyTest {

    private static Request<?> withHeader(String value) {
        Request<?> req = mock(Request.class);
        when(req.getHeader(AccountsEmailPolicy.SKIP_EMAIL_HEADER)).thenReturn(value);
        return req;
    }

    @Test
    void theHeaderIsIgnoredByDefault() {
        var policy = new AccountsEmailPolicy();
        assertFalse(policy.skipEmail(withHeader("true")));
    }

    @Test
    void theHeaderIsHonouredOnlyWhenAllowed() {
        var policy = new AccountsEmailPolicy(true, 0);
        assertTrue(policy.skipEmail(withHeader("true")));
        assertTrue(policy.skipEmail(withHeader("TRUE")));
        assertFalse(policy.skipEmail(withHeader("false")));
        assertFalse(policy.skipEmail(withHeader(null)));
        assertFalse(policy.skipEmail(null));
    }

    private static BsonDocument sentAt(Instant when) {
        return new BsonDocument("emailVerificationCreatedAt", new BsonDateTime(when.toEpochMilli()));
    }

    @Test
    void noSecondEmailWithinTheCooldown() {
        var policy = new AccountsEmailPolicy(false, 300);
        var now = Instant.parse("2026-10-23T10:00:00Z");
        assertFalse(policy.resendAllowed(sentAt(now.minusSeconds(60)), "emailVerificationCreatedAt", now));
    }

    @Test
    void anotherEmailOnceTheCooldownIsOver() {
        var policy = new AccountsEmailPolicy(false, 300);
        var now = Instant.parse("2026-10-23T10:00:00Z");
        assertTrue(policy.resendAllowed(sentAt(now.minusSeconds(300)), "emailVerificationCreatedAt", now));
    }

    @Test
    void anEmailWhenNoneWasSentBefore() {
        var policy = new AccountsEmailPolicy(false, 300);
        assertTrue(policy.resendAllowed(new BsonDocument(), "passwordResetCreatedAt", Instant.now()));
    }

    @Test
    void aZeroCooldownNeverBlocks() {
        var policy = new AccountsEmailPolicy(false, 0);
        var now = Instant.now();
        assertTrue(policy.resendAllowed(sentAt(now), "emailVerificationCreatedAt", now));
    }
}
