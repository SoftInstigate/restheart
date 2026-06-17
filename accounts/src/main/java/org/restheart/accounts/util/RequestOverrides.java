package org.restheart.accounts.util;

import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.oauth.OAuthConfig;
import org.restheart.exchange.ServiceRequest;

/**
 * Reads per-request override parameters and returns the effective values,
 * falling back to the plugin's static configuration.
 *
 * <h2>Override parameters</h2>
 * <table>
 *   <tr><th>Param name</th><th>Meaning</th><th>Fallback</th></tr>
 *   <tr>
 *     <td>{@code override-users-db}</td>
 *     <td>MongoDB database for user operations</td>
 *     <td>{@link AccountsConfigData#db()}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-cookie-domain}</td>
 *     <td>Domain attribute of the {@code rh_auth} cookie</td>
 *     <td>{@link AccountsConfigData#cookieDomain()}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-app-name}</td>
 *     <td>Application name used in email subjects and bodies</td>
 *     <td>{@link AccountsConfigData#appName()}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-frontend-url}</td>
 *     <td>Base URL of the public frontend (used in email links)</td>
 *     <td>{@link AccountsConfigData#frontendUrl()}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-frontend-app-url}</td>
 *     <td>Base URL of the authenticated app (redirect after login)</td>
 *     <td>{@link AccountsConfigData#frontendAppUrl()}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-tmpl-verification}</td>
 *     <td>HTML content of the email-verification template (inline, from MongoDB)</td>
 *     <td>{@code null} — falls back to file path or built-in</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-tmpl-password-reset}</td>
 *     <td>HTML content of the password-reset template (inline)</td>
 *     <td>{@code null}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-tmpl-invite}</td>
 *     <td>HTML content of the team-invitation template (inline)</td>
 *     <td>{@code null}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-oauth-google-enabled}</td>
 *     <td>Whether Google OAuth is enabled for this tenant</td>
 *     <td>{@code false} if not set</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-oauth-google-client-id}</td>
 *     <td>Google OAuth client ID for this tenant</td>
 *     <td>{@code null}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-oauth-google-client-secret}</td>
 *     <td>Google OAuth client secret for this tenant</td>
 *     <td>{@code null}</td>
 *   </tr>
 * </table>
 *
 * <h2>Multi-tenant usage (restheart-cloud)</h2>
 * <p>An interceptor such as {@code TenantConfigInterceptor} reads the {@code confs/{srvId}.accounts}
 * document from MongoDB and attaches these params at {@code REQUEST_BEFORE_EXCHANGE_INIT}.
 * The {@code accounts.*} sub-document structure is:
 * <pre>{@code
 * {
 *   "_id": "ea820b",
 *   "accounts": {
 *     "app-name": "Customer App",
 *     "frontend-url": "https://app.customer.com",
 *     "frontend-app-url": "https://app.customer.com/app",
 *     "templates": {
 *       "verification":   "<html>...</html>",
 *       "password-reset": "<html>...</html>",
 *       "invite":         "<html>...</html>"
 *     },
 *     "oauth": {
 *       "google": {
 *         "enabled":       true,
 *         "client-id":     "123….apps.googleusercontent.com",
 *         "client-secret": "GOCSPX-…"
 *       }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <h2>Single-tenant usage</h2>
 * <p>When no interceptor attaches override params, all methods return the values from
 * {@link AccountsConfigData}, preserving backward compatibility.
 */
public final class RequestOverrides {

    // ── Core DB / cookie ──────────────────────────────────────────────────────

    /** MongoDB database override (set by AuthDbResolver). */
    public static final String USERS_DB      = "override-users-db";

    /** Cookie domain override (set by AuthDbResolver). */
    public static final String COOKIE_DOMAIN = "override-cookie-domain";

    // ── Accounts-specific overrides (set by TenantConfigInterceptor) ──────────

    public static final String APP_NAME          = "override-accounts-app-name";
    public static final String FRONTEND_URL      = "override-accounts-frontend-url";
    public static final String FRONTEND_APP_URL  = "override-accounts-frontend-app-url";

    /** Inline HTML for the email-verification template (from confs/{srvId}.accounts.templates.verification). */
    public static final String TMPL_VERIFICATION  = "override-accounts-tmpl-verification";
    /** Inline HTML for the password-reset template. */
    public static final String TMPL_PASSWORD_RESET = "override-accounts-tmpl-password-reset";
    /** Inline HTML for the invite template. */
    public static final String TMPL_INVITE         = "override-accounts-tmpl-invite";

    // ── Per-tenant OAuth overrides ─────────────────────────────────────────────

    public static final String OAUTH_GOOGLE_ENABLED       = "override-accounts-oauth-google-enabled";
    public static final String OAUTH_GOOGLE_CLIENT_ID     = "override-accounts-oauth-google-client-id";
    public static final String OAUTH_GOOGLE_CLIENT_SECRET = "override-accounts-oauth-google-client-secret";

    // ── Per-tenant role override ──────────────────────────────────────────────

    /** System ACL role assigned after email verification (override for multi-tenant). */
    public static final String DEFAULT_ROLE = "override-accounts-default-role";

    /** Team role for the user who creates a team (override for multi-tenant). */
    public static final String OWNERSHIP_ROLE = "override-accounts-ownership-role";

    private RequestOverrides() {}

    // ── Accessor methods ──────────────────────────────────────────────────────

    /** Effective MongoDB database name. */
    public static String db(ServiceRequest<?> req, AccountsConfigData conf) {
        return str(req, USERS_DB, conf.db());
    }

    /** Effective cookie domain. */
    public static String cookieDomain(ServiceRequest<?> req, AccountsConfigData conf) {
        return str(req, COOKIE_DOMAIN, conf.cookieDomain());
    }

    /** Effective application name (used in email subjects / bodies). */
    public static String appName(ServiceRequest<?> req, AccountsConfigData conf) {
        return str(req, APP_NAME, conf.appName());
    }

    /** Effective frontend base URL (used in email links). */
    public static String frontendUrl(ServiceRequest<?> req, AccountsConfigData conf) {
        return str(req, FRONTEND_URL, conf.frontendUrl());
    }

    /** Effective frontend app URL (redirect after auto-login). */
    public static String frontendAppUrl(ServiceRequest<?> req, AccountsConfigData conf) {
        return str(req, FRONTEND_APP_URL, conf.frontendAppUrl());
    }

    /**
     * Inline HTML for the email-verification template, or {@code null} if not overridden.
     * When non-null, this takes precedence over any file-path or built-in template.
     */
    public static String templateVerification(ServiceRequest<?> req) {
        return str(req, TMPL_VERIFICATION, null);
    }

    /** Inline HTML for the password-reset template, or {@code null}. */
    public static String templatePasswordReset(ServiceRequest<?> req) {
        return str(req, TMPL_PASSWORD_RESET, null);
    }

    /** Inline HTML for the invite template, or {@code null}. */
    public static String templateInvite(ServiceRequest<?> req) {
        return str(req, TMPL_INVITE, null);
    }

    /** Effective system ACL role after verification. */
    public static String defaultRole(ServiceRequest<?> req, AccountsConfigData conf) {
        return str(req, DEFAULT_ROLE, conf.defaultRole());
    }

    /** Effective ownership role for team creators. */
    public static String ownershipRole(ServiceRequest<?> req, AccountsConfigData conf) {
        return str(req, OWNERSHIP_ROLE, conf.ownershipRole());
    }

    /**
     * Per-tenant Google OAuth config, or {@code null} if not overridden.
     * When non-null, this takes precedence over the static {@link OAuthConfig}.
     */
    public static OAuthConfig.ProviderConfig oauthGoogle(ServiceRequest<?> req) {
        var clientId     = str(req, OAUTH_GOOGLE_CLIENT_ID,     null);
        var clientSecret = str(req, OAUTH_GOOGLE_CLIENT_SECRET, null);
        if (clientId == null || clientSecret == null) return null;

        var enabled = bool(req, OAUTH_GOOGLE_ENABLED, true); // if creds are set, assume enabled
        return new OAuthConfig.ProviderConfig("google", enabled, clientId, clientSecret,
                "openid email profile");
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static String str(ServiceRequest<?> req, String key, String defaultValue) {
        var v = req.attachedParam(key);
        return (v instanceof String s && !s.isBlank()) ? s : defaultValue;
    }

    private static boolean bool(ServiceRequest<?> req, String key, boolean defaultValue) {
        var v = req.attachedParam(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String  s) return Boolean.parseBoolean(s);
        return defaultValue;
    }
}
