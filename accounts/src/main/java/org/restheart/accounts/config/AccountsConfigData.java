package org.restheart.accounts.config;

/**
 * Immutable record holding all configuration parameters shared across
 * restheart-accounts plugins (signup, email verification, invitations,
 * password reset, OAuth).
 *
 * <p>Produced by {@link AccountsConfig} and injected via
 * {@code @Inject("accountsConfig")}.
 */
public record AccountsConfigData(

    // ── Core ─────────────────────────────────────────────────────────────────

    /** MongoDB database name, e.g. {@code "8x5"}. */
    String db,

    /** Application display name used in email subjects and bodies. */
    String appName,

    // ── JWT ──────────────────────────────────────────────────────────────────

    /**
     * HS256 secret key — sourced from {@code jwtConfigProvider.key()}.
     * Never read from {@code accountsConfig} directly.
     */
    String jwtKey,

    /**
     * JWT issuer claim — sourced from {@code jwtConfigProvider.issuer()}.
     * Never read from {@code accountsConfig} directly.
     */
    String jwtIssuer,

    /** JWT time-to-live in minutes, e.g. {@code 15}. */
    int jwtTtl,

    // ── Cookie / URLs ────────────────────────────────────────────────────────

    /** Domain used when setting the auth cookie, e.g. {@code ".example.com"}. */
    String cookieDomain,

    /**
     * Name of the HttpOnly authentication cookie, e.g. {@code "8x5_auth"}.
     * Must match {@code authCookieSetter.name} and {@code authCookieHandler} configuration.
     * Defaults to {@code "rh_auth"} (RESTHeart's built-in default).
     */
    String cookieName,

    /** Base URL of the public frontend, e.g. {@code "https://app.example.com"}. */
    String frontendUrl,

    /** Base URL of the authenticated app, e.g. {@code "https://app.example.com/app"}. */
    String frontendAppUrl,

    // ── Legal ────────────────────────────────────────────────────────────────

    /** Accepted terms-of-service version, e.g. {@code "1.0"}. */
    String termsVersion,

    /** Accepted privacy-policy version, e.g. {@code "1.0"}. */
    String privacyVersion,

    // ── Email templates ──────────────────────────────────────────────────────

    /**
     * Default locale for email rendering (ISO 639-1, e.g. {@code "en"}).
     * Used when no user-specific locale is available.
     */
    String defaultLocale,

    /**
     * Path to the email verification template HTML file.
     * {@code null} or blank → use the built-in resource
     * {@code email-templates/verification.html}.
     */
    String verificationTemplatePath,

    /**
     * Path to the password-reset template HTML file.
     * {@code null} or blank → use the built-in resource
     * {@code email-templates/password-reset.html}.
     */
    String passwordResetTemplatePath,

    /**
     * Path to the team-invitation template HTML file.
     * {@code null} or blank → use the built-in resource
     * {@code email-templates/invite.html}.
     */
    String inviteTemplatePath

) {}
