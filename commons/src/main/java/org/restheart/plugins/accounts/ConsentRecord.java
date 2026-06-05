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
 * @param termsVersion   the Terms of Service version the user accepted
 *                       (from {@code accountsConfig.terms-version})
 * @param privacyVersion the Privacy Policy version the user accepted
 *                       (from {@code accountsConfig.privacy-version})
 * @param ip             the client IP address at the time of consent
 * @param acceptedAt     the instant the consent was recorded
 */
public record ConsentRecord(String termsVersion, String privacyVersion,
                             String ip, Instant acceptedAt) {}
