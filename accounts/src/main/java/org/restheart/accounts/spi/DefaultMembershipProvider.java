package org.restheart.accounts.spi;

import com.mongodb.client.MongoClient;
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
import org.restheart.plugins.accounts.TenantRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Default {@link MembershipProvider} implementation.
 *
 * <p>Preserves the built-in {@code tenant}/{@code tenants} schema introduced in
 * restheart-accounts 9.4:
 *
 * <pre>
 * // users collection
 * {
 *   _id: "alice@example.com",
 *   tenant:  "objectIdHex",               // active team
 *   tenants: [{ id: "objectIdHex", role: "owner"|"admin"|"member" }]
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

    public DefaultMembershipProvider(MongoClient mclient, String database) {
        this.db = new DbHelper(mclient, database);
    }

    // ── createInitialTeam ─────────────────────────────────────────────────────

    /**
     * Creates a team document in the {@code teams} collection and links the user
     * to it by setting {@code tenant} and adding an entry to {@code tenants} on
     * the user document.
     *
     * <p>The user document must already exist when this method is called.
     *
     * @param userId   the user's email address
     * @param teamName the display name for the new team
     * @return a {@link TenantRef} with the new team's ID and display name
     */
    @Override
    public TenantRef createInitialTeam(String userId, String teamName) {
        var now = new BsonDateTime(System.currentTimeMillis());

        var ownerMember = new BsonDocument()
                .append("userId",   new BsonString(userId))
                .append("role",     new BsonString("owner"))
                .append("joinedAt", now);

        var membersList = new BsonArray();
        membersList.add(ownerMember);

        var teamDoc = new BsonDocument()
                .append("name",      new BsonString(teamName))
                .append("createdBy", new BsonString(userId))
                .append("createdAt", now)
                .append("members",   membersList);

        var teamId    = db.insertTeam(teamDoc);
        var teamIdBson = new BsonObjectId(new ObjectId(teamId));

        // Link user → team
        db.addTenantMembership(userId, new BsonDocument()
                .append("id",   teamIdBson)
                .append("role", new BsonString("owner")));
        db.setActiveTenantIfAbsent(userId, teamIdBson);

        LOGGER.debug("DefaultMembershipProvider: created team '{}' ({}) for user <{}>",
                teamName, teamId, userId);

        return new TenantRef(teamIdBson, teamName);
    }

    // ── isMember ─────────────────────────────────────────────────────────────

    @Override
    public boolean isMember(String userId, BsonValue tenantId) {
        var userOpt = db.findUser(userId);
        if (userOpt.isEmpty()) return false;
        var user = userOpt.get();

        if (user.containsKey("tenant") && tenantId.equals(user.get("tenant"))) {
            return true;
        }
        if (user.containsKey("tenants") && user.get("tenants").isArray()) {
            for (var entry : user.getArray("tenants")) {
                if (entry.isDocument()) {
                    var e = entry.asDocument();
                    if (e.containsKey("id") && tenantId.equals(e.get("id"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ── addMember ─────────────────────────────────────────────────────────────

    /**
     * Adds a {@code {id, role}} entry to the user's {@code tenants} array via
     * {@code $addToSet} (idempotent). If the user has no active tenant yet, also
     * sets {@code tenant} to this tenant so they are immediately able to use it.
     *
     * @param userId   the user's email address
     * @param tenantId the tenant identifier
     * @param role     the role to assign (e.g. {@code "owner"} or {@code "member"})
     */
    @Override
    public void addMember(String userId, BsonValue tenantId, String role) {
        db.addTenantMembership(userId, new BsonDocument()
                .append("id",   tenantId)
                .append("role", new BsonString(role)));
        db.setActiveTenantIfAbsent(userId, tenantId);
    }

    // ── activeMembership ──────────────────────────────────────────────────────

    @Override
    public Optional<Membership> activeMembership(String userId) {
        var userOpt = db.findUser(userId);
        if (userOpt.isEmpty()) return Optional.empty();
        var user = userOpt.get();

        if (!user.containsKey("tenant") || user.get("tenant").isNull()) {
            return Optional.empty();
        }
        var tenantId    = user.get("tenant");
        var role        = findRoleInTenants(user, tenantId);
        var displayName = loadTeamName(tenantId);

        return Optional.of(new Membership(tenantId, displayName, role, true));
    }

    // ── listMemberships ───────────────────────────────────────────────────────

    @Override
    public List<Membership> listMemberships(String userId) {
        var userOpt = db.findUser(userId);
        if (userOpt.isEmpty()) return List.of();
        var user = userOpt.get();

        var activeTenant = user.containsKey("tenant") && !user.get("tenant").isNull()
                ? user.get("tenant") : null;

        var result = new ArrayList<Membership>();
        if (user.containsKey("tenants") && user.get("tenants").isArray()) {
            for (var entry : user.getArray("tenants")) {
                if (!entry.isDocument()) continue;
                var e        = entry.asDocument();
                var tenantId = e.containsKey("id") && !e.get("id").isNull() ? e.get("id") : null;
                var role     = e.containsKey("role") && e.get("role").isString()
                        ? e.getString("role").getValue() : "member";
                if (tenantId == null) continue;
                var displayName = loadTeamName(tenantId);
                result.add(new Membership(tenantId, displayName, role, tenantId.equals(activeTenant)));
            }
        }
        return result;
    }

    // ── setActiveMembership ──────────────────────────────────────────────

    @Override
    public void setActiveMembership(String userId, BsonValue tenantId) {
        if (!isMember(userId, tenantId)) {
            throw new IllegalArgumentException(
                    "User <" + userId + "> is not a member of tenant " + tenantId);
        }
        db.setActiveTenant(userId, tenantId);
    }

    // ── activateViaOAuth ──────────────────────────────────────────────────

    /**
     * Activates an invited user via OAuth.
     *
     * <p>Activates any user whose {@code status} is {@code "invited"} and who
     * already has an active {@code tenant} set (assigned when the invite was sent).
     * Sets {@code status} to {@code "active"}, removes the {@code inviteToken} field,
     * and optionally stores the accepted consents.
     *
     * @param userId   the user's email address
     * @param consents consent record to persist, or {@code null} if no consent was given
     * @return an {@link Optional} with the activated membership, or empty if the user
     *         is not in the {@code "invited"} state or has no pending tenant
     */
    @Override
    public Optional<Membership> activateViaOAuth(String userId, ConsentRecord consents) {
        var userOpt = db.findUser(userId);
        if (userOpt.isEmpty()) return Optional.empty();
        var user = userOpt.get();

        // Only activate users that are in the "invited" state
        if (!user.containsKey("status") || !"invited".equals(user.getString("status").getValue())) {
            return Optional.empty();
        }

        // User must already have an active tenant (set when the invite was sent)
        if (!user.containsKey("tenant") || user.get("tenant").isNull()) {
            LOGGER.warn("DefaultMembershipProvider.activateViaOAuth: invited user <{}> has no tenant", userId);
            return Optional.empty();
        }
        var tenantId = user.get("tenant");

        // Activate: set status and optionally record consents
        var updates = new BsonDocument().append("status", new BsonString("active"));
        if (consents != null) {
            updates.append("consents", buildConsentsDoc(consents));
        }
        db.updateUser(userId, updates);
        db.unsetUserFields(userId, List.of("inviteToken", "inviteCreatedAt"));

        var role        = findRoleInTenants(user, tenantId);
        var displayName = loadTeamName(tenantId);

        LOGGER.info("DefaultMembershipProvider: invited user <{}> activated via OAuth (tenant={})",
                userId, tenantId);

        return Optional.of(new Membership(tenantId, displayName, role, true));
    }

    private BsonDocument buildConsentsDoc(ConsentRecord consents) {
        var doc = new BsonDocument()
                .append("termsVersion",   new BsonString(consents.termsVersion()))
                .append("privacyVersion", new BsonString(consents.privacyVersion()))
                .append("acceptedAt",     new BsonDateTime(consents.acceptedAt().toEpochMilli()));
        if (consents.ip() != null) {
            doc.append("ip", new BsonString(consents.ip()));
        }
        return doc;
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private String findRoleInTenants(BsonDocument user, BsonValue tenantId) {
        if (user.containsKey("tenants") && user.get("tenants").isArray()) {
            for (var entry : user.getArray("tenants")) {
                if (!entry.isDocument()) continue;
                var e = entry.asDocument();
                if (e.containsKey("id") && tenantId.equals(e.get("id"))) {
                    return e.containsKey("role") && e.get("role").isString()
                            ? e.getString("role").getValue() : "member";
                }
            }
        }
        return "member";
    }

    private String loadTeamName(BsonValue tenantId) {
        try {
            return db.findTeam(tenantId)
                    .filter(t -> t.containsKey("name") && t.get("name").isString())
                    .map(t -> t.getString("name").getValue())
                    .orElseGet(() -> tenantIdToString(tenantId));
        } catch (Exception e) {
            return tenantIdToString(tenantId);
        }
    }
}
