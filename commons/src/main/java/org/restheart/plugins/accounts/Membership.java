package org.restheart.plugins.accounts;

/**
 * Represents a single tenant membership for a user.
 *
 * @param tenantId    the tenant's unique identifier
 * @param displayName the human-readable team / tenant name
 * @param role        the user's role within this tenant (e.g. {@code "owner"}, {@code "admin"},
 *                    or the configured member role)
 * @param active      {@code true} if this is the user's currently active tenant
 */
public record Membership(String tenantId, String displayName, String role, boolean active) {}
