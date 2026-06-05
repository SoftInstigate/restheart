package org.restheart.accounts.spi;

/**
 * Lightweight reference to a tenant returned by
 * {@link MembershipProvider#createInitialTeam(String, String)}.
 *
 * @param id          the tenant's unique identifier (e.g. an ObjectId hex string)
 * @param displayName the human-readable team / tenant name
 */
public record TenantRef(String id, String displayName) {}
