package org.restheart.plugins.accounts;

import java.time.Instant;

/**
 * A single member of a team, with display information denormalized from the
 * member's user document (i.e. joined against {@code users.profile}).
 *
 * @param email    the member's identifier (email address)
 * @param name     the member's display name, or {@code null} if unavailable
 * @param role     the member's role within the team (e.g. {@code "owner"} or the
 *                 configured member role)
 * @param joinedAt the instant the member joined the team, or {@code null} if unknown
 */
public record TeamMember(String email, String name, String role, Instant joinedAt) {}
