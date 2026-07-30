package org.restheart.accounts.config;

import java.util.Map;

import org.restheart.configuration.Configuration;
import org.restheart.plugins.Inject;
import org.restheart.plugins.accounts.AccountsConfigData;
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
 * <p>For the same reason the users database and collection are sourced from
 * {@code mongoRealmAuthenticator} ({@code users-db} and {@code users-collection}):
 * accounts must write users where the authenticator reads them. There is no
 * {@code accountsConfig} key for either — two independent settings could drift apart,
 * and the failure is silent: registration succeeds and login then fails.
 *
 * <p>Expected YAML configuration:
 * <pre>{@code
 * accountsConfig:
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
 *   users-unrestricted-roles: [admin]   # bypass the /users self-service write restriction
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

    private static final String MONGO_REALM_AUTHENTICATOR = "mongoRealmAuthenticator";

    @Inject("config")
    private Map<String, Object> config;

    @Inject("rh-config")
    private Configuration rhConfig;

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
            usersDb(),
            usersCollection(),
            configVal(config, "app-name",          "App"),
            jwtConfig.key(),
            jwtConfig.issuer(),
            configVal(config, "jwt-ttl",           15),
            configVal(config, "cookie-domain",     "localhost"),
            configVal(config, "cookie-name",      "rh_auth"),
            configVal(config, "cookie-secure",    true),
            configVal(config, "frontend-url",      "http://localhost:4200"),
            configVal(config, "frontend-app-url",  "http://localhost:4200/app"),
            configVal(config, "terms-version",     "1.0"),
            configVal(config, "privacy-version",   "1.0"),
            configVal(config, "default-locale",    "en"),
            configVal(templates, "verification",   null),
            configVal(templates, "password-reset", null),
            configVal(templates, "invite",         null),
            configVal(config, "team-claim-name",              "team"),
            configVal(config, "member-role-name",             "member"),
            configVal(config, "membership-endpoints-enabled", true),
            configVal(config, "ownership-role",                 "owner"),
            configVal(config, "default-role",                   "user"),
            configVal(config, "account-properties-claims",      null),
            configVal(config, "users-unrestricted-roles",       null)
        );
    }

    @Override
    public AccountsConfigData get(PluginRecord<?> caller) {
        return data;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Users db and collection: sourced from mongoRealmAuthenticator
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Where accounts stores its data — users, teams, invitations and OAuth codes.
     *
     * <p>Sourced from {@code mongoRealmAuthenticator/users-db} so that accounts always
     * writes users where the authenticator reads them. Two independent settings could
     * drift apart, and the failure is silent: registration succeeds and login then fails.
     */
    private String usersDb() {
        return configVal(mongoRealmAuthenticatorConf(), "users-db", "restheart");
    }

    /**
     * The users collection, sourced from {@code mongoRealmAuthenticator/users-collection}
     * for the same reason as {@link #usersDb()}.
     */
    private String usersCollection() {
        return configVal(mongoRealmAuthenticatorConf(), "users-collection", "users");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mongoRealmAuthenticatorConf() {
        return rhConfig != null && rhConfig.toMap().get(MONGO_REALM_AUTHENTICATOR) instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : null;
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
