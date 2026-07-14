package org.restheart.accounts.config;

import java.util.List;

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

    /**
     * Whether the auth cookie carries the {@code Secure} attribute.
     * Defaults to {@code true}. Set to {@code false} only for local HTTP development,
     * where browsers reject {@code Secure} cookies over plain HTTP.
     * Mirrors {@code authCookieSetter.secure}.
     */
    boolean cookieSecure,

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
    String inviteTemplatePath,

    // ── Membership SPI ───────────────────────────────────────────────────────

    /**
     * JWT claim name used to carry the active team identifier.
     * Defaults to {@code "team"}.
     */
    String teamClaimName,

    /**
     * Role name assigned to non-admin team members.
     * Defaults to {@code "member"}. Configure to {@code "user"} if your ACL rules
     * already use that label.
     */
    String memberRoleName,

    /**
     * Whether the membership management endpoints are enabled.
     * When {@code false}, the following endpoints return 404:
     * {@code /auth/invite}, {@code /auth/resend-invite},
     * {@code /auth/teams}, {@code /auth/switch-team}.
     * Useful when you expose equivalent endpoints via a custom provider.
     * Defaults to {@code true}.
     */
    boolean membershipEndpointsEnabled,

    /**
     * Team role assigned to the user who creates a new team (e.g. {@code "owner"}).
     * Stored in {@code user.teams[].role} and {@code team.members[].role}.
     * Defaults to {@code "owner"}.
     */
    String ownershipRole,

    /**
     * System ACL role assigned to users after email verification or OAuth login.
     * Stored in {@code user.roles}. Defaults to {@code "user"}.
     */
    String defaultRole,

    // ── JWT extra claims ─────────────────────────────────────────────────────

    /**
     * List of request attached-parameter names that should be propagated as JWT claims.
     * Mirrors {@code jwtTokenManager.account-properties-claims} and is applied
     * by {@link org.restheart.accounts.util.JwtHelper} when issuing tokens from
     * accounts endpoints (verify, activate, reset-password, switch-team, OAuth).
     *
     * <p>Example: {@code [srvNode, customClaim]}.
     *
     * <p>{@code null} or empty list → no additional properties are propagated
     * (only {@code authDb} and explicit extra claims are included).
     */
    List<String> accountPropertiesClaims

) {}
