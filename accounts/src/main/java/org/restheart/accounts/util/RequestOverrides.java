package org.restheart.accounts.util;

import org.restheart.plugins.accounts.AccountsConfigData;
import org.restheart.accounts.oauth.OAuthConfig;
import org.restheart.exchange.ServiceRequest;

import java.util.List;

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
 *     <td>{@code override-accounts-oauth-api-base-url}</td>
 *     <td>Base URL this node is reachable at, used to build the OAuth {@code redirect_uri}</td>
 *     <td>{@link OAuthConfig#apiBaseUrl()}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-oauth-frontend-success-url}</td>
 *     <td>Where the browser lands after a successful OAuth login</td>
 *     <td>{@link OAuthConfig#frontendSuccessUrl()}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-oauth-frontend-error-url}</td>
 *     <td>Where the browser lands after a failed OAuth login</td>
 *     <td>{@link OAuthConfig#frontendErrorUrl()}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-oauth-{provider}-enabled}</td>
 *     <td>Whether OAuth via {@code {provider}} (e.g. {@code google}, {@code github}) is enabled
 *         for this team</td>
 *     <td>{@code true} if client-id/client-secret overrides are set, otherwise not applicable</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-oauth-{provider}-client-id}</td>
 *     <td>OAuth client ID for this team, for the given provider</td>
 *     <td>{@code null}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-oauth-{provider}-client-secret}</td>
 *     <td>OAuth client secret for this team, for the given provider</td>
 *     <td>{@code null}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-oauth-{provider}-scope}</td>
 *     <td>Space-separated OAuth scopes requested for this team, for the given provider</td>
 *     <td>the provider's static scope (YAML), or its well-known default
 *         (see {@link OAuthConfig#defaultScope(String)})</td>
 *   </tr>
 *   <tr>
 *     <td>{@code override-accounts-users-unrestricted-roles}</td>
 *     <td>Roles exempt from the {@code /users} self-service write restriction
 *         (see {@code AccountsInitializer})</td>
 *     <td>{@link AccountsConfigData#usersUnrestrictedRoles()}</td>
 *   </tr>
 * </table>
 *
 * <h2>Multi-team usage (restheart-cloud)</h2>
 * <p>An interceptor such as {@code TeamConfigInterceptor} reads the {@code confs/{srvId}.accounts}
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
 * <p>{@code override-accounts-oauth-api-base-url} is deliberately <em>not</em> part of this
 * document: on a multi-tenant node every team is served on a different hostname, so the
 * interceptor should derive it from the incoming request (scheme + host) rather than store it —
 * a stored value could not be correct for more than one team. Likewise
 * {@code override-accounts-oauth-frontend-success-url} / {@code -error-url} are typically derived
 * from the team's own {@code frontend-app-url} rather than stored separately, unless the team
 * needs a distinct post-OAuth destination.
 *
 * <h2>Single-team usage</h2>
 * <p>When no interceptor attaches override params, all methods return the values from
 * {@link AccountsConfigData}, preserving backward compatibility.
 */
public final class RequestOverrides {

    // ── Core DB / cookie ──────────────────────────────────────────────────────

    /** MongoDB database override (set by AuthDbResolver). */
    public static final String USERS_DB      = "override-users-db";

    /** Cookie domain override (set by AuthDbResolver). */
    public static final String COOKIE_DOMAIN = "override-cookie-domain";

    /** Cookie {@code Secure} attribute override (set by AuthDbResolver / TeamConfigInterceptor). */
    public static final String COOKIE_SECURE = "override-cookie-secure";

    // ── Accounts-specific overrides (set by TeamConfigInterceptor) ──────────

    public static final String APP_NAME          = "override-accounts-app-name";
    public static final String FRONTEND_URL      = "override-accounts-frontend-url";
    public static final String FRONTEND_APP_URL  = "override-accounts-frontend-app-url";

    /** Inline HTML for the email-verification template (from confs/{srvId}.accounts.templates.verification). */
    public static final String TMPL_VERIFICATION  = "override-accounts-tmpl-verification";
    /** Inline HTML for the password-reset template. */
    public static final String TMPL_PASSWORD_RESET = "override-accounts-tmpl-password-reset";
    /** Inline HTML for the invite template. */
    public static final String TMPL_INVITE         = "override-accounts-tmpl-invite";

    // ── Per-team OAuth overrides ─────────────────────────────────────────────

    /** Base URL this node is reachable at, used to build the OAuth {@code redirect_uri}. */
    public static final String OAUTH_API_BASE_URL = "override-accounts-oauth-api-base-url";
    /** Where the browser lands after a successful OAuth login. */
    public static final String OAUTH_FRONTEND_SUCCESS_URL = "override-accounts-oauth-frontend-success-url";
    /** Where the browser lands after a failed OAuth login. */
    public static final String OAUTH_FRONTEND_ERROR_URL = "override-accounts-oauth-frontend-error-url";

    // ── Per-team role override ──────────────────────────────────────────────

    /** System ACL role assigned after email verification (override for multi-team). */
    public static final String DEFAULT_ROLE = "override-accounts-default-role";

    /** Team role for the user who creates a team (override for multi-team). */
    public static final String OWNERSHIP_ROLE = "override-accounts-ownership-role";

    // ── Users self-service write restriction override ───────────────────────

    /** Roles exempt from the {@code /users} self-service write restriction (override for multi-team). */
    public static final String USERS_UNRESTRICTED_ROLES = "override-accounts-users-unrestricted-roles";

    /**
     * Whether Sign-up Management is enabled for this tenant (override for multi-team).
     * When explicitly {@code false}, the {@code /users} self-service write restriction
     * is skipped entirely — a tenant that never opted into restheart-accounts never
     * opted into its opinions on {@code /users}. Must be set before authentication
     * (e.g. {@code REQUEST_BEFORE_EXCHANGE_INIT}), since the veto is evaluated as part
     * of authorization, before any {@code REQUEST_AFTER_AUTH} interceptor runs.
     */
    public static final String SIGNUP_MGMT_ENABLED = "override-accounts-signup-mgmt-enabled";

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

    /** Effective cookie {@code Secure} attribute. */
    public static boolean cookieSecure(ServiceRequest<?> req, AccountsConfigData conf) {
        return bool(req, COOKIE_SECURE, conf.cookieSecure());
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

    /** Effective roles exempt from the {@code /users} self-service write restriction. */
    public static List<String> usersUnrestrictedRoles(ServiceRequest<?> req, AccountsConfigData conf) {
        return list(req, USERS_UNRESTRICTED_ROLES, conf.usersUnrestrictedRoles());
    }

    /**
     * Whether Sign-up Management is enabled for this tenant. Defaults to {@code true}
     * (restriction fully enforced) unless a deployment-layer interceptor explicitly
     * attaches {@code false}.
     */
    public static boolean signupMgmtEnabled(ServiceRequest<?> req, AccountsConfigData conf) {
        return bool(req, SIGNUP_MGMT_ENABLED, true);
    }

    /** Effective OAuth API base URL, used to build the {@code redirect_uri} sent to providers. */
    public static String oauthApiBaseUrl(ServiceRequest<?> req, OAuthConfig conf) {
        return str(req, OAUTH_API_BASE_URL, conf.apiBaseUrl());
    }

    /** Effective post-login redirect URL on OAuth success. */
    public static String oauthFrontendSuccessUrl(ServiceRequest<?> req, OAuthConfig conf) {
        return str(req, OAUTH_FRONTEND_SUCCESS_URL, conf.frontendSuccessUrl());
    }

    /** Effective post-login redirect URL on OAuth failure. */
    public static String oauthFrontendErrorUrl(ServiceRequest<?> req, OAuthConfig conf) {
        return str(req, OAUTH_FRONTEND_ERROR_URL, conf.frontendErrorUrl());
    }

    /**
     * Per-team OAuth provider config, or {@code null} if not overridden for the given
     * provider. When non-null, this takes precedence over the static {@link OAuthConfig}
     * for that provider.
     *
     * <p>Provider-agnostic: reads {@code override-accounts-oauth-{provider}-*}, so it works
     * for any provider name (built-in or custom), not just {@code "google"}. Requires both
     * {@code client-id} and {@code client-secret} to be set to produce a result — a partial
     * override (e.g. only {@code enabled}) is treated as "not overridden".
     *
     * <p>{@code scope}, when not itself overridden, falls back to the provider's static scope
     * (from {@code oauthConfig.providers.{provider}.scope} in YAML) or, if the provider has no
     * static entry at all, to its well-known default ({@link OAuthConfig#defaultScope(String)})
     * — a generalized override cannot assume every provider wants the same scope.
     *
     * @param req          the incoming request
     * @param providerName provider name (case-insensitive), e.g. {@code "google"}, {@code "github"}
     * @param staticConfig the static {@link OAuthConfig}, consulted only for the {@code scope} fallback
     */
    public static OAuthConfig.ProviderConfig oauthProvider(ServiceRequest<?> req, String providerName,
            OAuthConfig staticConfig) {
        var name = providerName.toLowerCase();

        var clientId     = str(req, oauthProviderKey(name, "client-id"),     null);
        var clientSecret = str(req, oauthProviderKey(name, "client-secret"), null);
        if (clientId == null || clientSecret == null) return null;

        var enabled = bool(req, oauthProviderKey(name, "enabled"), true); // if creds are set, assume enabled

        var staticProviderConf = staticConfig != null ? staticConfig.provider(name) : null;
        var defaultScope = staticProviderConf != null ? staticProviderConf.scope() : OAuthConfig.defaultScope(name);
        var scope = str(req, oauthProviderKey(name, "scope"), defaultScope);

        return new OAuthConfig.ProviderConfig(name, enabled, clientId, clientSecret, scope);
    }

    /** Builds the {@code override-accounts-oauth-{provider}-{suffix}} attribute name. */
    private static String oauthProviderKey(String providerName, String suffix) {
        return "override-accounts-oauth-" + providerName + "-" + suffix;
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

    @SuppressWarnings("unchecked")
    private static List<String> list(ServiceRequest<?> req, String key, List<String> defaultValue) {
        var v = req.attachedParam(key);
        if (v instanceof List<?> l && !l.isEmpty()) {
            return (List<String>) l;
        }
        return defaultValue;
    }
}
