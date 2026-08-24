package org.restheart.plugins.accounts;

import java.time.Instant;

/**
 * Captures the consent versions and metadata recorded when a user accepts the
 * Terms of Service and Privacy Policy before or during an OAuth flow.
 *
 * <p>Passed to {@link MembershipProvider#activateViaOAuth} when the frontend
 * signals that the user accepted T&amp;C during the OAuth authorization step
 * (via {@code consentsAccepted=true} on {@code GET /auth/oauth/authorize/{provider}}).
 *
 * <p><b>Not authoritative on a multi-tenant node.</b> {@code termsVersion} and
 * {@code privacyVersion} reflect {@code accountsConfig.terms-version} /
 * {@code accountsConfig.privacy-version} as effective for the tenant that issued the
 * request — i.e. after the deployment-layer interceptor (e.g. {@code TeamConfigInterceptor})
 * has attached its per-tenant overrides, if any (see {@code RequestOverrides}). A
 * {@link MembershipProvider} implementing a richer consent model — an arbitrary list of
 * versioned documents rather than these two fixed ones — is expected to resolve versions
 * on its own and should not treat these fields as authoritative.
 *
 * @param termsVersion   the Terms of Service version the user accepted
 *                       (effective {@code accountsConfig.terms-version} for the tenant)
 * @param privacyVersion the Privacy Policy version the user accepted
 *                       (effective {@code accountsConfig.privacy-version} for the tenant)
 * @param ip             the client IP address at the time of consent
 * @param acceptedAt     the instant the consent was recorded
 */
public record ConsentRecord(String termsVersion, String privacyVersion,
                            String ip, Instant acceptedAt) {
}
