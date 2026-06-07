package org.restheart.plugins.accounts;

import org.bson.BsonValue;

/**
 * Represents a single tenant membership for a user.
 *
 * @param tenantId    the tenant's unique identifier (e.g. an ObjectId or a hex string)
 * @param displayName the human-readable team / tenant name
 * @param role        the user's role within this tenant (e.g. {@code "owner"}, {@code "admin"},
 *                    or the configured member role)
 * @param active      {@code true} if this is the user's currently active tenant
 */
public record Membership(BsonValue tenantId, String displayName, String role, boolean active) {}
