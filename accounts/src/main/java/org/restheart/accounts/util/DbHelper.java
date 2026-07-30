package org.restheart.accounts.util;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDateTime;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;

/**
 * Low-level MongoDB helper for the restheart-accounts plugin.
 * Not a RESTHeart plugin itself — instantiated directly by services that
 * receive a {@link MongoClient} via injection.
 */
public class DbHelper {

    private static final String DEFAULT_USERS_COLLECTION = "users";
    private static final String TEAMS_COLLECTION = "teams";
    private static final String INVITATIONS_COLLECTION = "auth_invitations";

    /** Duplicate-key error code returned by MongoDB. */
    private static final int DUPLICATE_KEY_CODE = 11000;

    private final MongoClient mclient;
    private final String db;
    private final String usersCollection;

    public DbHelper(MongoClient mclient, String db) {
        this(mclient, db, DEFAULT_USERS_COLLECTION);
    }

    public DbHelper(MongoClient mclient, String db, String usersCollection) {
        this.mclient = mclient;
        this.db = db;
        this.usersCollection = usersCollection != null ? usersCollection : DEFAULT_USERS_COLLECTION;
    }

    // -------------------------------------------------------------------------
    // Users
    // -------------------------------------------------------------------------

    /**
     * Finds a user document by email address (stored as {@code _id}).
     *
     * @param email the user's email
     * @return the user document, or {@link Optional#empty()} if not found
     */
    public Optional<BsonDocument> findUser(String email) {
        return Optional.ofNullable(
            users()
                .find(eq("_id", new BsonString(email)))
                .first()
        );
    }

    /**
     * Finds a user document by a token stored in the given field.
     *
     * @param tokenField the field name, e.g. {@code "inviteToken"},
     *                   {@code "passwordResetToken"}, or
     *                   {@code "emailVerificationToken"}
     * @param token      the token value to look up
     * @return the matching user document, or {@link Optional#empty()}
     */
    public Optional<BsonDocument> findUserByToken(String tokenField, String token) {
        return Optional.ofNullable(
            users()
                .find(Filters.eq(tokenField, token))
                .first()
        );
    }

    /**
     * Batch-fetches user documents by email address (stored as {@code _id}).
     *
     * @param emails the emails to look up
     * @return a map of email to user document, containing only the emails that were found
     */
    public Map<String, BsonDocument> findUsers(List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return Map.of();
        }
        var result = new HashMap<String, BsonDocument>();
        var idsFilter = new java.util.ArrayList<BsonString>();
        emails.forEach(e -> idsFilter.add(new BsonString(e)));
        users().find(Filters.in("_id", idsFilter))
               .forEach(u -> result.put(u.getString("_id").getValue(), u));
        return result;
    }

    /**
     * Inserts a new user document.
     *
     * @param user the document to insert (must have {@code _id} = email)
     * @return {@code true} on success, {@code false} if the email already exists
     *         (duplicate-key error)
     */
    public boolean insertUser(BsonDocument user) {
        try {
            users().insertOne(user);
            return true;
        } catch (MongoWriteException ex) {
            if (ex.getError().getCode() == DUPLICATE_KEY_CODE) {
                return false;
            }
            throw ex;
        }
    }

    /**
     * Applies a {@code $set} patch to the user identified by {@code email}.
     *
     * @param email   the user's email (_id)
     * @param updates a document whose fields will be {@code $set} on the user
     * @return {@code true} if a document was matched and modified
     */
    public boolean updateUser(String email, BsonDocument updates) {
        var result = users().updateOne(
            eq("_id", new BsonString(email)),
            new BsonDocument("$set", updates)
        );
        return result.getMatchedCount() > 0;
    }

    /**
     * Removes the specified fields from the user document using {@code $unset}.
     *
     * @param email  the user's email (_id)
     * @param fields list of field names to remove
     * @return {@code true} if a document was matched
     */
    public boolean unsetUserFields(String email, List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return false;
        }
        // Build $unset document: { field1: "", field2: "", ... }
        var unsetDoc = new BsonDocument();
        fields.forEach(f -> unsetDoc.put(f, new BsonString("")));

        var result = users().updateOne(
            eq("_id", new BsonString(email)),
            new BsonDocument("$unset", unsetDoc)
        );
        return result.getMatchedCount() > 0;
    }

    /**
     * Adds a team membership to the user's {@code teams} array using {@code $addToSet}.
     *
     * @param email      the user's email (_id)
     * @param membership a document {@code { id: "...", role: "..." }}
     * @return {@code true} if a document was matched
     */
    public boolean addTeamMembership(String email, BsonDocument membership) {
        var result = users().updateOne(
            eq("_id", new BsonString(email)),
            new BsonDocument("$addToSet", new BsonDocument("teams", membership))
        );
        return result.getMatchedCount() > 0;
    }

    /**
     * Removes the entry with the given {@code teamId} from the user's {@code teams} array.
     *
     * @param email  the user's email (_id)
     * @param teamId the team to remove
     * @return {@code true} if a document was matched
     */
    public boolean removeTeamMembership(String email, BsonValue teamId) {
        var result = users().updateOne(
            eq("_id", new BsonString(email)),
            Updates.pull("teams", new BsonDocument("id", teamId))
        );
        return result.getMatchedCount() > 0;
    }

    /**
     * Updates the role for the given team entry in the user's {@code teams} array.
     *
     * @param email  the user's email (_id)
     * @param teamId the team whose role to change
     * @param newRole the new role string
     * @return {@code true} if a matching team entry was found and modified
     */
    public boolean updateTeamRole(String email, BsonValue teamId, String newRole) {
        var result = users().updateOne(
            Filters.and(
                eq("_id", new BsonString(email)),
                Filters.eq("teams.id", teamId)
            ),
            Updates.set("teams.$.role", new BsonString(newRole))
        );
        return result.getModifiedCount() > 0;
    }

    /**
     * Sets the user's active team unconditionally. The {@code team} field is stored as
     * a {@code { _id, role }} object so the active team's id <em>and</em> the caller's
     * role in it travel together into the JWT {@code team} claim (see the {@code team}
     * claim shape used for ACL {@code @user.team._id} / {@code @user.team.role}).
     *
     * @param email  the user's email (_id)
     * @param teamId the team to make active
     * @param role   the user's role within that team
     * @return {@code true} if a document was matched
     */
    public boolean setActiveTeam(String email, BsonValue teamId, String role) {
        return updateUser(email, new BsonDocument("team", activeTeamDoc(teamId, role)));
    }

    /**
     * Sets the user's active team only if the {@code team} field is absent or null.
     * Safe to call idempotently after every {@code addTeamMembership} for new users.
     * Stored as a {@code { _id, role }} object (see {@link #setActiveTeam}).
     *
     * @param email  the user's email (_id)
     * @param teamId the team to set as active
     * @param role   the user's role within that team
     * @return {@code true} if the field was set (document matched and had no prior team)
     */
    public boolean setActiveTeamIfAbsent(String email, BsonValue teamId, String role) {
        var result = users().updateOne(
            Filters.and(
                eq("_id", new BsonString(email)),
                Filters.or(
                    Filters.exists("team", false),
                    Filters.eq("team", null)
                )
            ),
            Updates.set("team", activeTeamDoc(teamId, role))
        );
        return result.getModifiedCount() > 0;
    }

    /**
     * Updates the role recorded on the active-team object ({@code team.role}) only when
     * the given team is currently the user's active team ({@code team._id} matches).
     * Used to keep the denormalized active-team role in sync when a member's role is
     * changed while that team is their active one.
     *
     * @param email  the user's email (_id)
     * @param teamId the team whose role changed
     * @param role   the new role
     * @return {@code true} if the active-team role was updated (i.e. it was the active team)
     */
    public boolean setActiveTeamRoleIfActive(String email, BsonValue teamId, String role) {
        var result = users().updateOne(
            Filters.and(
                eq("_id", new BsonString(email)),
                Filters.eq("team._id", teamId)
            ),
            Updates.set("team.role", new BsonString(role))
        );
        return result.getModifiedCount() > 0;
    }

    private static BsonDocument activeTeamDoc(BsonValue teamId, String role) {
        var doc = new BsonDocument("_id", teamId);
        if (role != null) {
            doc.append("role", new BsonString(role));
        }
        return doc;
    }

    // -------------------------------------------------------------------------
    // Teams
    // -------------------------------------------------------------------------

    /**
     * Inserts a new team document into the {@code teams} collection.
     *
     * @param team the document to insert (without {@code _id}; one will be generated)
     * @return the newly generated {@code _id} as a hex string
     */
    public String insertTeam(BsonDocument team) {
        var result = teams().insertOne(team);
        return result.getInsertedId().asObjectId().getValue().toHexString();
    }

    /**
     * Finds a team document by its {@code _id}.
     *
     * @param teamId the team identifier
     * @return the team document, or {@link Optional#empty()} if not found
     */
    public Optional<BsonDocument> findTeam(BsonValue teamId) {
        return Optional.ofNullable(
            teams()
                .find(eq("_id", teamId))
                .first()
        );
    }

    /**
     * Adds a {@code {userId, role, joinedAt}} entry to the team's {@code members}
     * array, unless an entry for {@code userId} already exists (idempotent —
     * a plain {@code $addToSet} would not be, since {@code joinedAt} makes every
     * call produce a distinct subdocument).
     *
     * @param teamId the team's {@code _id}
     * @param userId the member's user id (email address)
     * @param role   the role to assign (e.g. {@code "owner"} or {@code "member"})
     * @return {@code true} if the member entry was added
     */
    public boolean addMemberToTeam(BsonValue teamId, String userId, String role) {
        var memberDoc = new BsonDocument()
            .append("userId",   new BsonString(userId))
            .append("role",     new BsonString(role))
            .append("joinedAt", new BsonDateTime(System.currentTimeMillis()));
        var result = teams().updateOne(
            Filters.and(
                eq("_id", teamId),
                Filters.not(Filters.eq("members.userId", new BsonString(userId)))
            ),
            Updates.push("members", memberDoc)
        );
        return result.getModifiedCount() > 0;
    }

    /**
     * Applies a {@code $set} patch to the team identified by {@code teamId}.
     *
     * @param teamId  the team's {@code _id}
     * @param updates a document whose fields will be {@code $set} on the team
     * @return {@code true} if a document was matched
     */
    public boolean updateTeam(BsonValue teamId, BsonDocument updates) {
        var result = teams().updateOne(
            eq("_id", teamId),
            new BsonDocument("$set", updates)
        );
        return result.getMatchedCount() > 0;
    }

    /**
     * Atomically deletes the team identified by {@code teamId}, but only if its
     * {@code members} array has at most one entry (i.e. no members other than the
     * caller). Safe against concurrent invite-acceptance races, since the emptiness
     * check and the delete happen in a single {@code findOneAndDelete} operation.
     *
     * @param teamId the team's {@code _id}
     * @return {@code true} if the team was deleted; {@code false} if it still has
     *         other members, or no longer exists
     */
    public boolean deleteTeamIfEmpty(BsonValue teamId) {
        var sizeExpr = List.<BsonValue>of(
            new BsonDocument("$size", new BsonString("$members")),
            new BsonInt32(1));
        var filter = new BsonDocument("_id", teamId)
            .append("$expr", new BsonDocument("$lte", new BsonArray(sizeExpr)));
        return teams().findOneAndDelete(filter) != null;
    }

    /**
     * Removes a member entry from the team's {@code members} array.
     *
     * @param teamId the team's {@code _id}
     * @param userId the user to remove
     * @return {@code true} if the team document was matched
     */
    public boolean removeMemberFromTeam(BsonValue teamId, String userId) {
        var result = teams().updateOne(
            eq("_id", teamId),
            Updates.pull("members", new BsonDocument("userId", new BsonString(userId)))
        );
        return result.getMatchedCount() > 0;
    }

    /**
     * Updates the role for a member in the team's {@code members} array.
     *
     * @param teamId  the team's {@code _id}
     * @param userId  the member's user id
     * @param newRole the new role string
     * @return {@code true} if the member entry was found and modified
     */
    public boolean updateMemberRoleInTeam(BsonValue teamId, String userId, String newRole) {
        var result = teams().updateOne(
            Filters.and(
                eq("_id", teamId),
                Filters.eq("members.userId", new BsonString(userId))
            ),
            Updates.set("members.$.role", new BsonString(newRole))
        );
        return result.getModifiedCount() > 0;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private MongoCollection<BsonDocument> users() {
        return mclient.getDatabase(db)
                      .getCollection(usersCollection, BsonDocument.class);
    }

    private MongoCollection<BsonDocument> teams() {
        return mclient.getDatabase(db)
                      .getCollection(TEAMS_COLLECTION, BsonDocument.class);
    }

    private MongoCollection<BsonDocument> invitations() {
        return mclient.getDatabase(db)
                      .getCollection(INVITATIONS_COLLECTION, BsonDocument.class);
    }

    /**
     * Creates an invitation document in the auth_invitations collection.
     *
     * @param isNewUser {@code true} if the user was created by this invite (no prior account)
     */
    public void createInvitation(String email, String token, BsonValue teamId, String role, long ttlMs, boolean isNewUser) {
        var now = System.currentTimeMillis();
        var doc = new BsonDocument()
                .append("_id", new org.bson.BsonObjectId())
                .append("email", new BsonString(email))
                .append("token", new BsonString(token))
                .append("teamId", teamId)
                .append("role", new BsonString(role))
                .append("isNewUser", new org.bson.BsonBoolean(isNewUser))
                .append("createdAt", new BsonDateTime(now))
                .append("expiresAt", new BsonDateTime(now + ttlMs));
        invitations().insertOne(doc);
    }

    /**
     * Finds a valid (non-expired) invitation by token alone.
     */
    public Optional<BsonDocument> findInvitationByToken(String token) {
        var now = System.currentTimeMillis();
        var doc = invitations().find(
                Filters.and(
                        Filters.eq("token", token),
                        Filters.gt("expiresAt", new BsonDateTime(now))))
                .first();
        return Optional.ofNullable(doc);
    }

    /**
     * Finds a valid (non-expired) invitation by the (email, token) pair.
     * Used by {@code GET /auth/invitation} — the pair is known only to the invitee.
     */
    public Optional<BsonDocument> findInvitationByEmailAndToken(String email, String token) {
        var now = System.currentTimeMillis();
        var doc = invitations().find(
                Filters.and(
                        Filters.eq("email", email),
                        Filters.eq("token", token),
                        Filters.gt("expiresAt", new BsonDateTime(now))))
                .first();
        return Optional.ofNullable(doc);
    }

    /**
     * Finds the latest pending invitation for a user in a specific team.
     */
    public Optional<BsonDocument> findInvitation(String email, BsonValue teamId) {
        var now = System.currentTimeMillis();
        var doc = invitations().find(
                Filters.and(
                        Filters.eq("email", email),
                        Filters.eq("teamId", teamId),
                        Filters.gt("expiresAt", new BsonDateTime(now))))
                .sort(new BsonDocument("createdAt", new org.bson.BsonInt32(-1)))
                .first();
        return Optional.ofNullable(doc);
    }

    /**
     * Renews the token and expiry of an existing invitation document.
     */
    public boolean renewInvitation(BsonValue invitationId, String newToken, long ttlMs) {
        var now = System.currentTimeMillis();
        var result = invitations().updateOne(
                Filters.eq("_id", invitationId),
                new BsonDocument("$set", new BsonDocument()
                        .append("token", new BsonString(newToken))
                        .append("createdAt", new BsonDateTime(now))
                        .append("expiresAt", new BsonDateTime(now + ttlMs))));
        return result.getModifiedCount() > 0;
    }

    /**
     * Deletes an invitation by its document id.
     */
    public void deleteInvitation(BsonValue invitationId) {
        invitations().deleteOne(Filters.eq("_id", invitationId));
    }

    /**
     * Deletes all invitations for a user in a specific team (used after acceptance).
     */
    public void deleteInvitations(String email, BsonValue teamId) {
        invitations().deleteMany(
                Filters.and(
                        Filters.eq("email", email),
                        Filters.eq("teamId", teamId)));
    }

    /**
     * Lists all pending (non-expired) invitations for a specific team.
     * Does NOT include the token field (sensitive — one-shot secret).
     */
    public List<BsonDocument> listInvitationsByTeam(BsonValue teamId) {
        return invitations()
                .find(Filters.eq("teamId", teamId))
                .projection(Filters.eq("token", 0))
                .into(new java.util.ArrayList<>());
    }
}
