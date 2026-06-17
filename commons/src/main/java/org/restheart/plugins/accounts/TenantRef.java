package org.restheart.plugins.accounts;

import org.bson.BsonValue;

/**
 * Lightweight reference to a tenant returned by
 * {@link MembershipProvider#createInitialTeam(String, String)}.
 *
 * @param id          the tenant's unique identifier (e.g. an ObjectId or a hex string)
 * @param displayName the human-readable team / tenant name
 */
public record TenantRef(BsonValue id, String displayName) {}
