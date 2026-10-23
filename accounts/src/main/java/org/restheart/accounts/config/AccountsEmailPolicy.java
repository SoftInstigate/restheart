package org.restheart.accounts.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.bson.BsonDocument;
import org.restheart.exchange.Request;
import org.restheart.plugins.ConfigurablePlugin;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When restheart-accounts may skip an email, and how often it may send one to the same address.
 *
 * <pre>
 * /accountsEmailPolicy:
 *   # Honour the X-Skip-Email: true header. For integration tests only: in production it lets
 *   # anyone create accounts and reset tokens without the owner of the address being told.
 *   allow-skip-email-header: false
 *   # Minimum seconds between two password-reset or verification emails to the same address.
 *   resend-cooldown-seconds: 300
 * </pre>
 *
 * @since 9.12.2
 */
@RegisterPlugin(
        name = "accountsEmailPolicy",
        description = "When restheart-accounts may skip an email and how often it may resend one",
        enabledByDefault = true,
        priority = 20)
public class AccountsEmailPolicy implements Provider<AccountsEmailPolicy>, ConfigurablePlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountsEmailPolicy.class);

    public static final String SKIP_EMAIL_HEADER = "X-Skip-Email";
    static final int DEFAULT_COOLDOWN_SECONDS = 300;

    @Inject("config")
    private Map<String, Object> config;

    private boolean allowSkipEmailHeader = false;
    private Duration resendCooldown = Duration.ofSeconds(DEFAULT_COOLDOWN_SECONDS);

    public AccountsEmailPolicy() {
    }

    /** For tests and for callers that build the policy themselves. */
    public AccountsEmailPolicy(boolean allowSkipEmailHeader, int resendCooldownSeconds) {
        this.allowSkipEmailHeader = allowSkipEmailHeader;
        this.resendCooldown = Duration.ofSeconds(Math.max(0, resendCooldownSeconds));
    }

    @OnInit
    public void onInit() {
        var args = config != null ? config : Map.<String, Object>of();
        this.allowSkipEmailHeader = Boolean.TRUE.equals(argOrDefault(args, "allow-skip-email-header", false));
        int seconds = argOrDefault(args, "resend-cooldown-seconds", DEFAULT_COOLDOWN_SECONDS);
        this.resendCooldown = Duration.ofSeconds(Math.max(0, seconds));
        if (allowSkipEmailHeader) {
            LOGGER.warn("accountsEmailPolicy: the {} header is honoured; enable this only in test environments",
                    SKIP_EMAIL_HEADER);
        }
    }

    @Override
    public AccountsEmailPolicy get(PluginRecord<?> caller) {
        return this;
    }

    /**
     * Whether this request asks to skip the email and is allowed to. Without
     * {@code allow-skip-email-header: true} the header is ignored and the email is sent.
     */
    public boolean skipEmail(Request<?> request) {
        return allowSkipEmailHeader
                && request != null
                && "true".equalsIgnoreCase(request.getHeader(SKIP_EMAIL_HEADER));
    }

    /**
     * Whether another email may go to this user, given the time the last one was prepared,
     * stored in the user document under {@code timestampField}. True when there is no such time.
     */
    public boolean resendAllowed(BsonDocument user, String timestampField) {
        return resendAllowed(user, timestampField, Instant.now());
    }

    boolean resendAllowed(BsonDocument user, String timestampField, Instant now) {
        if (resendCooldown.isZero() || user == null || !user.containsKey(timestampField)
                || !user.get(timestampField).isDateTime()) {
            return true;
        }
        var last = Instant.ofEpochMilli(user.getDateTime(timestampField).getValue());
        return !now.isBefore(last.plus(resendCooldown));
    }

    public Duration resendCooldown() {
        return resendCooldown;
    }
}
