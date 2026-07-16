package org.restheart.accounts.spi;

import com.mongodb.client.MongoClient;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonDateTime;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.accounts.util.DbHelper;
import org.restheart.utils.BsonUtils;
import org.restheart.plugins.accounts.Membership;
import org.restheart.plugins.accounts.MembershipProvider;
import org.restheart.plugins.accounts.TeamRef;
import org.restheart.plugins.accounts.ConsentRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Default {@link MembershipProvider} implementation.
 *
 * <p>Preserves the built-in {@code team}/{@code teams} schema:
 *
 * <pre>
 * // users collection
 * {
 *   _id: "alice@example.com",
 *   team:  "objectIdHex",               // active team
 *   teams: [{ id: "objectIdHex", role: "owner"|"admin"|"member" }]
 * }
 *
 * // teams collection
 * {
 *   _id: ObjectId,
 *   name: "Acme Corp",
 *   createdBy: "alice@example.com",
 *   createdAt: ISODate,
 *   members: [{ userId: "...", role: "...", joinedAt: ISODate }]
 * }
 * </pre>
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

    public DefaultMembershipProvider(MongoClient mclient, String database, String ownershipRole, String defaultRole) {
        this.db = new DbHelper(mclient, database);
        this.ownershipRole = ownershipRole != null ? ownershipRole : "owner";
        this.defaultRole = defaultRole != null ? defaultRole : "user";
    }

    // ── createInitialTeam ─────────────────────────────────────────────────────

    /**
     * Creates a team document in the {@code teams} collection and links the user
     * to it by setting {@code team} and adding an entry to {@code teams} on
     * the user document.
     *
     * <p>The user document must already exist when this method is called.
     *
     * @param userId   the user's email address
     * @param teamName the display name for the new team
     * @return a {@link TeamRef} with the new team's ID and display name
     */
    @Override
    public TeamRef createInitialTeam(String userId, String teamName) {
        var now = new BsonDateTime(System.currentTimeMillis());

        var ownerMember = new BsonDocument()
                .append("userId",   new BsonString(userId))
                .append("role",     new BsonString(ownershipRole))
                .append("joinedAt", now);

        var membersList = new BsonArray();
        membersList.add(ownerMember);

        var teamDoc = new BsonDocument()
                .append("name",      new BsonString(teamName))
                .append("createdBy", new BsonString(userId))
                .append("createdAt", now)
                .append("members",   membersList);

        var teamId     = db.insertTeam(teamDoc);
        var teamIdBson = new BsonObjectId(new ObjectId(teamId));

        // Link user → team
        db.addTeamMembership(userId, new BsonDocument()
                .append("id",   teamIdBson)
                .append("role", new BsonString(ownershipRole)));
        db.setActiveTeamIfAbsent(userId, teamIdBson);

        LOGGER.debug("DefaultMembershipProvider: created team '{}' ({}) for user <{}>",
                teamName, teamId, userId);

        return new TeamRef(teamIdBson, teamName);
    }

    // ── isMember ─────────────────────────────────────────────────────────────

    @Override
    public boolean isMember(String userId, BsonValue teamId) {
        var userOpt = db.findUser(userId);
        if (userOpt.isEmpty()) return false;
        var user = userOpt.get();

        if (user.containsKey("team") && teamId.equals(user.get("team"))) {
            return true;
        }
        if (user.containsKey("teams") && user.get("teams").isArray()) {
            for (var entry : user.getArray("teams")) {
                if (entry.isDocument()) {
                    var e = entry.asDocument();
                    if (e.containsKey("id") && teamId.equals(e.get("id"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ── addMember ─────────────────────────────────────────────────────────────

    /**
     * Adds a {@code {id, role}} entry to the user's {@code teams} array via
     * {@code $addToSet} (idempotent). If the user has no active team yet, also
     * sets {@code team} to this team so they are immediately able to use it.
     *
     * @param userId the user's email address
     * @param teamId the team identifier
     * @param role   the role to assign (e.g. {@code "owner"} or {@code "member"})
     */
    @Override
    public void addMember(String userId, BsonValue teamId, String role) {
        db.addTeamMembership(userId, new BsonDocument()
                .append("id",   teamId)
                .append("role", new BsonString(role)));
        db.setActiveTeamIfAbsent(userId, teamId);
    }

    // ── activeMembership ──────────────────────────────────────────────────────

    @Override
    public Optional<Membership> activeMembership(String userId) {
        var userOpt = db.findUser(userId);
        if (userOpt.isEmpty()) return Optional.empty();
        var user = userOpt.get();

        if (!user.containsKey("team") || user.get("team").isNull()) {
            return Optional.empty();
        }
        var teamId      = user.get("team");
        var role        = findRoleInTeams(user, teamId);
        var displayName = loadTeamName(teamId);

        return Optional.of(new Membership(teamId, displayName, role, true));
    }

    // ── listMemberships ───────────────────────────────────────────────────────

    @Override
    public List<Membership> listMemberships(String userId) {
        var userOpt = db.findUser(userId);
        if (userOpt.isEmpty()) return List.of();
        var user = userOpt.get();

        var activeTeam = user.containsKey("team") && !user.get("team").isNull()
                ? user.get("team") : null;

        var result = new ArrayList<Membership>();
        if (user.containsKey("teams") && user.get("teams").isArray()) {
            for (var entry : user.getArray("teams")) {
                if (!entry.isDocument()) continue;
                var e      = entry.asDocument();
                var teamId = e.containsKey("id") && !e.get("id").isNull() ? e.get("id") : null;
                var role   = e.containsKey("role") && e.get("role").isString()
                        ? e.getString("role").getValue() : "member";
                if (teamId == null) continue;
                var displayName = loadTeamName(teamId);
                result.add(new Membership(teamId, displayName, role, teamId.equals(activeTeam)));
            }
        }
        return result;
    }

    // ── setActiveMembership ──────────────────────────────────────────────

    @Override
    public void setActiveMembership(String userId, BsonValue teamId) {
        if (!isMember(userId, teamId)) {
            throw new IllegalArgumentException(
                    "User <" + userId + "> is not a member of team " + teamId);
        }
        db.setActiveTeam(userId, teamId);
    }

    // ── removeMember ─────────────────────────────────────────────────────

    /**
     * Removes the user from the given team on both sides:
     * {@code user.teams[]} (user document) and {@code team.members[]} (team document).
     * If the team was the user's active team, the active team field is cleared.
     */
    @Override
    public void removeMember(String userId, BsonValue teamId) {
        var userOpt = db.findUser(userId);
        if (userOpt.isEmpty()) return;
        var user = userOpt.get();

        db.removeTeamMembership(userId, teamId);
        db.removeMemberFromTeam(teamId, userId);

        // Clear active team if it was this one
        if (user.containsKey("team") && teamId.equals(user.get("team"))) {
            db.unsetUserFields(userId, List.of("team"));
        }

        LOGGER.info("DefaultMembershipProvider: removed user <{}> from team {}", userId, teamId);
    }

    // ── updateMemberRole ─────────────────────────────────────────────────

    /**
     * Updates the org-level role for the user in the given team on both sides:
     * {@code user.teams[].role} and {@code team.members[].role}.
     */
    @Override
    public void updateMemberRole(String userId, BsonValue teamId, String newRole) {
        db.updateTeamRole(userId, teamId, newRole);
        db.updateMemberRoleInTeam(teamId, userId, newRole);

        LOGGER.info("DefaultMembershipProvider: updated role of <{}> in team {} to '{}'",
                userId, teamId, newRole);
    }

    // ── activateViaOAuth ──────────────────────────────────────────────────

    /**
     * Activates an invited user via OAuth.
     *
     * <p>Activates any unverified user ({@code roles: ["$unauthenticated"]}) who
     * already has an active {@code team} set (assigned when the invite was sent).
     * Assigns the configured {@code default-role}, removes the {@code inviteToken}
     * field.
     *
     * <p>Consent persistence is the responsibility of the deployment layer.
     * This method ignores the {@code consents} parameter.
     *
     * @param userId   the user's email address
     * @return an {@link Optional} with the activated membership, or empty if the user
     *         is not unverified or has no pending team
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

        // User must already have an active team (set when the invite was sent)
        if (!user.containsKey("team") || user.get("team").isNull()) {
            LOGGER.warn("DefaultMembershipProvider.activateViaOAuth: invited user <{}> has no team", userId);
            return Optional.empty();
        }
        var teamId = user.get("team");

        // Activate: assign defaultRole
        var rolesArray = new BsonArray();
        rolesArray.add(new BsonString(defaultRole));
        var updates = new BsonDocument().append("roles", rolesArray);
        db.updateUser(userId, updates);

        var role        = findRoleInTeams(user, teamId);
        var displayName = loadTeamName(teamId);

        LOGGER.info("DefaultMembershipProvider: invited user <{}> activated via OAuth (team={})",
                userId, teamId);

        return Optional.of(new Membership(teamId, displayName, role, true));
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private String findRoleInTeams(BsonDocument user, BsonValue teamId) {
        if (user.containsKey("teams") && user.get("teams").isArray()) {
            for (var entry : user.getArray("teams")) {
                if (!entry.isDocument()) continue;
                var e = entry.asDocument();
                if (e.containsKey("id") && teamId.equals(e.get("id"))) {
                    return e.containsKey("role") && e.get("role").isString()
                            ? e.getString("role").getValue() : "member";
                }
            }
        }
        return "member";
    }

    private String loadTeamName(BsonValue teamId) {
        var fallback = teamId.isString() ? teamId.asString().getValue() : BsonUtils.toJson(teamId);
        try {
            return db.findTeam(teamId)
                    .filter(t -> t.containsKey("name") && t.get("name").isString())
                    .map(t -> t.getString("name").getValue())
                    .orElse(fallback);
        } catch (Exception e) {
            return fallback;
        }
    }
}
