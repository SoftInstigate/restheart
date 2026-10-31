/*-
 * ========================LICENSE_START=================================
 * restheart-accounts
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.accounts.spi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.accounts.util.DbHelper;
import org.restheart.plugins.accounts.ConsentRecord;
import org.restheart.plugins.accounts.Membership;
import org.restheart.plugins.accounts.MembershipProvider;
import org.restheart.plugins.accounts.TeamMember;
import org.restheart.plugins.accounts.TeamRef;
import org.restheart.plugins.schema.JsonSchemas;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

/**
 * Default {@link MembershipProvider} implementation.
 *
 * <p>Who belongs to a team, and as what, is written once, on the team (9.12.0, #766):
 *
 * <pre>
 * // teams collection
 * {
 *   _id: ObjectId,
 *   name: "Acme Corp",
 *   createdBy: "alice@example.com",
 *   createdAt: ISODate,
 *   members: [{ userId: "alice@example.com", role: "owner"|"member", joinedAt: ISODate }]
 * }
 *
 * // users collection
 * {
 *   _id: "alice@example.com",
 *   team: { _id: ObjectId }             // the active team: the user's own choice, so on the user
 * }
 * </pre>
 *
 * <p>Before 9.12.0 the user also carried {@code teams: [{ id, role }]} and the active role in
 * {@code team.role}: three copies, written with separate updates, that could disagree. They are
 * neither written nor read any more; {@code MembershipMigration} unsets them from existing data.
 * Access follows {@code team.members}: a user listed nowhere in a team's members cannot use it,
 * and has no active membership while that team is the active one.
 *
 * <p>This class is <em>not</em> a RESTHeart plugin. It is instantiated directly by
 * {@link org.restheart.accounts.AccountsService}, which passes a {@link MongoClient} and
 * the configured database name to the constructor.
 */
public class DefaultMembershipProvider implements MembershipProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMembershipProvider.class);

    private final DbHelper db;
    private final String ownershipRole;
    private final String defaultRole;

    public DefaultMembershipProvider(MongoClient mclient, String database, String usersCollection, String ownershipRole, String defaultRole, JsonSchemas jsonSchemas) {
        this.db = new DbHelper(mclient, database, usersCollection, jsonSchemas);
        this.ownershipRole = ownershipRole != null ? ownershipRole : "owner";
        this.defaultRole = defaultRole != null ? defaultRole : "user";
    }

    // ── createInitialTeam ─────────────────────────────────────────────────────

    /**
     * Creates a team with the user as its owner and makes it the user's active team, unless the
     * user already has one.
     *
     * <p>The user document must already exist when this method is called.
     *
     * @param userId   the user's email address
     * @param teamName the display name for the new team
     * @return a {@link TeamRef} with the new team's ID and display name
     */
    @Override
    public TeamRef createInitialTeam(String userId, String teamName) {
        return createTeam(userId, teamName, false);
    }

    // ── createTeam ───────────────────────────────────────────────────────────

    /**
     * Creates an additional team for an already-registered user and makes it the active one
     * (unlike {@link #createInitialTeam}, which only does so when the user has none yet).
     *
     * @param userId   the user's email address; must already exist
     * @param teamName the display name for the new team
     * @return a {@link TeamRef} with the new team's ID and display name
     */
    @Override
    public TeamRef createTeam(String userId, String teamName) {
        return createTeam(userId, teamName, true);
    }

    private TeamRef createTeam(String userId, String teamName, boolean forceActive) {
        var now = new BsonDateTime(System.currentTimeMillis());

        var ownerMember = new BsonDocument()
                .append("userId", new BsonString(userId))
                .append("role", new BsonString(ownershipRole))
                .append("joinedAt", now);

        var membersList = new BsonArray();
        membersList.add(ownerMember);

        var teamDoc = new BsonDocument()
                .append("name", new BsonString(teamName))
                .append("createdBy", new BsonString(userId))
                .append("createdAt", now)
                .append("members", membersList);

        var teamId = db.insertTeam(teamDoc);
        var teamIdBson = new BsonObjectId(new ObjectId(teamId));

        if (forceActive) {
            db.setActiveTeam(userId, teamIdBson);
        } else {
            db.setActiveTeamIfAbsent(userId, teamIdBson);
        }

        LOGGER.debug("DefaultMembershipProvider: created team '{}' ({}) for user <{}>",
                teamName, teamId, userId);

        return new TeamRef(teamIdBson, teamName);
    }

    // ── isMember ─────────────────────────────────────────────────────────────

    /** Whether the team's {@code members} name the user: the one place a membership exists. */
    @Override
    public boolean isMember(String userId, BsonValue teamId) {
        return db.findTeam(teamId).flatMap(t -> member(t, userId)).isPresent();
    }

    // ── addMember ─────────────────────────────────────────────────────────────

    /**
     * Adds a {@code {userId, role, joinedAt}} entry to the team's {@code members} (idempotent:
     * a user already there keeps the role it has). If the user has no active team yet, this
     * team becomes it, so they are immediately able to use it.
     *
     * @param userId the user's email address
     * @param teamId the team identifier
     * @param role   the role to assign (e.g. {@code "owner"} or {@code "member"})
     */
    @Override
    public void addMember(String userId, BsonValue teamId, String role) {
        db.addMemberToTeam(teamId, userId, role);
        db.setActiveTeamIfAbsent(userId, teamId);
    }

    // ── activeMembership ──────────────────────────────────────────────────────

    /**
     * The user's active team with the role its {@code members} entry says, or none: when the user
     * has no active team, or is no longer among its members (removed while it was the active one).
     */
    @Override
    public Optional<Membership> activeMembership(String userId) {
        var userOpt = db.findUser(userId);
        if (userOpt.isEmpty()) return Optional.empty();

        var teamId = activeTeamId(userOpt.get());
        if (teamId == null) {
            return Optional.empty();
        }

        return db.findTeam(teamId)
                .flatMap(team -> member(team, userId).map(m -> membership(team, m, true)));
    }

    // ── listMemberships ───────────────────────────────────────────────────────

    /** The teams whose {@code members} name the user, one query on the teams collection. */
    @Override
    public List<Membership> listMemberships(String userId) {
        var activeTeam = db.findUser(userId).map(DefaultMembershipProvider::activeTeamId).orElse(null);

        var result = new ArrayList<Membership>();

        for (var team : db.findTeamsOf(userId)) {
            var teamId = team.get("_id");
            member(team, userId).ifPresent(m -> result.add(membership(team, m, teamId.equals(activeTeam))));
        }

        return result;
    }

    // ── setActiveMembership ──────────────────────────────────────────────

    @Override
    public void setActiveMembership(String userId, BsonValue teamId) {
        if (db.findUser(userId).isEmpty() || !isMember(userId, teamId)) {
            throw new IllegalArgumentException(
                    "User <" + userId + "> is not a member of team " + teamId);
        }

        db.setActiveTeam(userId, teamId);
    }

    // ── removeMember ─────────────────────────────────────────────────────

    /**
     * Removes the user from the team's {@code members}. If the team was the user's active team,
     * the active team is cleared.
     */
    @Override
    public void removeMember(String userId, BsonValue teamId) {
        db.removeMemberFromTeam(teamId, userId);
        db.unsetActiveTeamIf(userId, teamId);

        LOGGER.info("DefaultMembershipProvider: removed user <{}> from team {}", userId, teamId);
    }

    // ── updateMemberRole ─────────────────────────────────────────────────

    /** Updates the user's role in the team's {@code members}: what {@code @team.role} reads next. */
    @Override
    public void updateMemberRole(String userId, BsonValue teamId, String newRole) {
        db.updateMemberRoleInTeam(teamId, userId, newRole);

        LOGGER.info("DefaultMembershipProvider: updated role of <{}> in team {} to '{}'",
                userId, teamId, newRole);
    }

    // ── listTeamMembers ───────────────────────────────────────────────────

    /**
     * Loads the team's {@code members[]} array and batch-fetches the corresponding
     * user documents to denormalize display names.
     */
    @Override
    public List<TeamMember> listTeamMembers(BsonValue teamId) {
        var teamOpt = db.findTeam(teamId);
        if (teamOpt.isEmpty()) return List.of();
        var team = teamOpt.get();

        if (!team.containsKey("members") || !team.get("members").isArray()) return List.of();
        var memberEntries = team.getArray("members");

        var userIds = new ArrayList<String>();
        for (var entry : memberEntries) {
            if (entry.isDocument() && entry.asDocument().containsKey("userId")) {
                userIds.add(entry.asDocument().getString("userId").getValue());
            }
        }

        var users = db.findUsers(userIds);

        var result = new ArrayList<TeamMember>();
        for (var entry : memberEntries) {
            if (!entry.isDocument()) continue;
            var e = entry.asDocument();
            if (!e.containsKey("userId")) continue;

            var userId = e.getString("userId").getValue();
            var role = roleOf(e);
            var joinedAt = e.containsKey("joinedAt") && e.get("joinedAt").isDateTime()
                    ? Instant.ofEpochMilli(e.getDateTime("joinedAt").getValue()) : null;
            var name = displayName(users.get(userId));

            result.add(new TeamMember(userId, name, role, joinedAt));
        }
        return result;
    }

    // ── updateTeam ────────────────────────────────────────────────────────

    /**
     * Applies a partial {@code $set} update to the team's {@code name} and/or
     * {@code description} fields. Fields left {@code null} are not modified.
     */
    @Override
    public void updateTeam(BsonValue teamId, String name, String description) {
        var updates = new BsonDocument();
        if (name != null) updates.put("name", new BsonString(name));
        if (description != null) updates.put("description", new BsonString(description));
        if (updates.isEmpty()) return;

        db.updateTeam(teamId, updates);
        LOGGER.info("DefaultMembershipProvider: updated team {}", teamId);
    }

    // ── deleteTeam ────────────────────────────────────────────────────────

    /**
     * Atomically deletes the team if it has no other members, then clears the caller's active
     * team if this was it.
     */
    @Override
    public boolean deleteTeam(String userId, BsonValue teamId) {
        if (!db.deleteTeamIfEmpty(teamId)) {
            return false;
        }

        db.unsetActiveTeamIf(userId, teamId);

        LOGGER.info("DefaultMembershipProvider: deleted team {} (requested by <{}>)", teamId, userId);
        return true;
    }

    // ── activateViaOAuth ──────────────────────────────────────────────────

    /**
     * Activates an invited user via OAuth.
     *
     * <p>Activates any unverified user ({@code roles: ["$unauthenticated"]}) who already has an
     * active team whose members name them (the caller adds the membership from the invitation
     * first). Assigns the configured {@code default-role}.
     *
     * <p>Consent persistence is the responsibility of the deployment layer.
     * This method ignores the {@code consents} parameter.
     *
     * @param userId   the user's email address
     * @return an {@link Optional} with the activated membership, or empty if the user
     *         is not unverified or has no active membership
     */
    @Override
    public Optional<Membership> activateViaOAuth(String userId, ConsentRecord consents) {
        var userOpt = db.findUser(userId);
        if (userOpt.isEmpty()) return Optional.empty();
        var user = userOpt.get();

        // Only activate unverified users (roles == ["$unauthenticated"])
        var userRoles = user.containsKey("roles") && user.get("roles").isArray()
                ? user.getArray("roles") : new BsonArray();
        boolean isUnverified = userRoles.size() == 1
                && "$unauthenticated".equals(userRoles.get(0).asString().getValue());

        if (!isUnverified) {
            return Optional.empty();
        }

        var membership = activeMembership(userId);
        if (membership.isEmpty()) {
            LOGGER.warn("DefaultMembershipProvider.activateViaOAuth: invited user <{}> has no team", userId);
            return Optional.empty();
        }

        // Activate: assign defaultRole
        var rolesArray = new BsonArray();
        rolesArray.add(new BsonString(defaultRole));
        db.updateUser(userId, new BsonDocument().append("roles", rolesArray));

        LOGGER.info("DefaultMembershipProvider: invited user <{}> activated via OAuth (team={})",
                userId, membership.get().teamId());

        return membership;
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Extracts the active team's id from the user document, tolerating the {@code team: { _id }}
     * shape, the pre-9.12.0 {@code team: { _id, role }} one and the pre-9.6.0 bare id. Returns
     * {@code null} when the user has no active team.
     */
    static BsonValue activeTeamId(BsonDocument user) {
        if (!user.containsKey("team") || user.get("team").isNull()) {
            return null;
        }
        var team = user.get("team");
        if (team.isDocument()) {
            var d = team.asDocument();
            return d.containsKey("_id") && !d.get("_id").isNull() ? d.get("_id") : null;
        }
        return team; // legacy scalar id
    }

    /** The {@code members} entry of {@code userId} in {@code team}, if any. */
    static Optional<BsonDocument> member(BsonDocument team, String userId) {
        if (!team.containsKey("members") || !team.get("members").isArray()) {
            return Optional.empty();
        }

        for (var entry : team.getArray("members")) {
            if (entry.isDocument()) {
                var e = entry.asDocument();
                if (e.containsKey("userId") && e.get("userId").isString()
                        && userId.equals(e.getString("userId").getValue())) {
                    return Optional.of(e);
                }
            }
        }

        return Optional.empty();
    }

    private static String roleOf(BsonDocument memberEntry) {
        return memberEntry.containsKey("role") && memberEntry.get("role").isString()
                ? memberEntry.getString("role").getValue() : "member";
    }

    private static Membership membership(BsonDocument team, BsonDocument memberEntry, boolean active) {
        var teamId = team.get("_id");
        var name = team.containsKey("name") && team.get("name").isString()
                ? team.getString("name").getValue()
                : teamId.isString() ? teamId.asString().getValue() : BsonUtils.toJson(teamId);
        var description = team.containsKey("description") && team.get("description").isString()
                ? team.getString("description").getValue() : null;

        return new Membership(teamId, name, description, roleOf(memberEntry), active);
    }

    /** Builds a display name ("name surname") from a user's profile, falling back to their email. */
    private String displayName(BsonDocument user) {
        if (user == null) return null;
        var email = user.containsKey("_id") && user.get("_id").isString()
                ? user.getString("_id").getValue() : null;
        if (!user.containsKey("profile") || !user.get("profile").isDocument()) return email;
        var profile = user.getDocument("profile");
        var name = profile.containsKey("name") && profile.get("name").isString()
                ? profile.getString("name").getValue() : null;
        var surname = profile.containsKey("surname") && profile.get("surname").isString()
                ? profile.getString("surname").getValue() : null;
        if (name == null && surname == null) return email;
        return java.util.stream.Stream.of(name, surname)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
