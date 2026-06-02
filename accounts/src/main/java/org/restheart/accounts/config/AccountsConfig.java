package org.restheart.accounts.config;

import java.util.Map;

import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

/**
 * RESTHeart {@link Provider} that reads the {@code accountsConfig} YAML block
 * and exposes an {@link AccountsConfigData} instance to other plugins via DI.
 *
 * <p>Expected YAML configuration:
 * <pre>{@code
 * accountsConfig:
 *   db: myapp
 *   app-name: "My App"
 *   jwt-key: your-secret
 *   jwt-issuer: myapp.example.com
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
    name = "accountsConfig",
    description = "Provides AccountsConfigData loaded from the plugin YAML block",
    enabledByDefault = true
)
public class AccountsConfig implements Provider<AccountsConfigData> {

    @Inject("config")
    private Map<String, Object> config;

    private AccountsConfigData data;

    @OnInit
    @SuppressWarnings("unchecked")
    public void onInit() {
        // Read optional templates sub-map
        var templates = config != null && config.get("templates") instanceof Map<?, ?>
                ? (Map<String, Object>) config.get("templates")
                : Map.of();

        data = new AccountsConfigData(
            configVal(config, "db",                "restheart"),
            configVal(config, "app-name",          "App"),
            configVal(config, "jwt-key",           "change-me"),
            configVal(config, "jwt-issuer",        "restheart.com"),
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
            configVal(templates, "invite",         null)
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
