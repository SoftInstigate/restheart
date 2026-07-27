package org.restheart.plugins.accounts;

import org.bson.BsonValue;

/**
 * Lightweight reference to a team returned by
 * {@link MembershipProvider#createInitialTeam(String, String)}.
 *
 * @param id          the team's unique identifier (e.g. an ObjectId or a hex string)
 * @param displayName the human-readable team name
 */
public record TeamRef(BsonValue id, String displayName) {}
