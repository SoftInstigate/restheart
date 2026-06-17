package org.restheart.accounts.config;

import java.util.Map;

import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.tokens.JwtConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RESTHeart {@link Provider} that reads the {@code accountsConfig} YAML block
 * and exposes an {@link AccountsConfigData} instance to other plugins via DI.
 *
 * <p>JWT key and issuer are sourced automatically from {@code jwtConfigProvider},
 * ensuring restheart-accounts always signs tokens with the same key used by
 * {@code jwtAuthenticationMechanism} to verify them.
 *
 * <p>Expected YAML configuration:
 * <pre>{@code
 * accountsConfig:
 *   db: myapp
 *   app-name: "My App"
 *   jwt-ttl: 15
 *   cookie-domain: app.example.com
 *   frontend-url: https://app.example.com
 *   frontend-app-url: https://app.example.com/app
 *   terms-version: "1.0"
 *   privacy-version: "1.0"
 *   default-locale: en
 *   templates:
 *     verification:   etc/email-templates/verification.html   # null = built-in
 *     password-reset: etc/email-templates/password-reset.html
 *     invite:         etc/email-templates/invite.html
 * }</pre>
 */
@RegisterPlugin(
    name             = "accountsConfig",
    description      = "Provides AccountsConfigData loaded from the plugin YAML block",
    enabledByDefault = false,
    priority         = 20  // must be > jwtConfigProvider priority (10) so jwtConfig is ready at @OnInit
)
public class AccountsConfig implements Provider<AccountsConfigData> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountsConfig.class);

    @Inject("config")
    private Map<String, Object> config;

    @Inject("jwtConfigProvider")
    private JwtConfigProvider.JwtConfig jwtConfig;

    private AccountsConfigData data;

    @OnInit
    @SuppressWarnings("unchecked")
    public void onInit() {
        // Warn if jwt-key or jwt-issuer are still in the config — they are now ignored
        if (config != null && config.containsKey("jwt-key")) {
            LOGGER.warn("accountsConfig/jwt-key is ignored: JWT key is now sourced from jwtConfigProvider. Remove it from your configuration.");
        }
        if (config != null && config.containsKey("jwt-issuer")) {
            LOGGER.warn("accountsConfig/jwt-issuer is ignored: JWT issuer is now sourced from jwtConfigProvider. Remove it from your configuration.");
        }

        // Read optional templates sub-map
        var templates = config != null && config.get("templates") instanceof Map<?, ?>
                ? (Map<String, Object>) config.get("templates")
                : Map.of();

        data = new AccountsConfigData(
            configVal(config, "db",                "restheart"),
            configVal(config, "app-name",          "App"),
            jwtConfig.key(),
            jwtConfig.issuer(),
            configVal(config, "jwt-ttl",           15),
            configVal(config, "cookie-domain",     "localhost"),
            configVal(config, "cookie-name",      "rh_auth"),
            configVal(config, "frontend-url",      "http://localhost:4200"),
            configVal(config, "frontend-app-url",  "http://localhost:4200/app"),
            configVal(config, "terms-version",     "1.0"),
            configVal(config, "privacy-version",   "1.0"),
            configVal(config, "default-locale",    "en"),
            configVal(templates, "verification",   null),
            configVal(templates, "password-reset", null),
            configVal(templates, "invite",         null),
            configVal(config, "tenant-claim-name",            "tenant"),
            configVal(config, "member-role-name",             "member"),
            configVal(config, "membership-endpoints-enabled", true),
            configVal(config, "ownership-role",                 "owner"),
            configVal(config, "default-role",                   "user"),
            configVal(config, "account-properties-claims",      null)
        );
    }

    @Override
    public AccountsConfigData get(PluginRecord<?> caller) {
        return data;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T> T configVal(Map<?, ?> map, String key, T defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        try {
            return (T) map.get(key);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
}
