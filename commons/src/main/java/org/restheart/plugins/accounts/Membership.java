package org.restheart.plugins.accounts;

import org.bson.BsonValue;

/**
 * Represents a single team membership for a user.
 *
 * @param teamId      the team's unique identifier (e.g. an ObjectId or a hex string)
 * @param displayName the human-readable team name
 * @param role        the user's role within this team (e.g. {@code "owner"}, {@code "admin"},
 *                    or the configured member role)
 * @param active      {@code true} if this is the user's currently active team
 */
public record Membership(BsonValue teamId, String displayName, String description, String role, boolean active) {}
