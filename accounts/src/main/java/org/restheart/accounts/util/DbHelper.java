package org.restheart.accounts.util;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.BsonDocument;
import org.bson.BsonDateTime;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;

import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;

/**
 * Low-level MongoDB helper for the restheart-accounts plugin.
 * Not a RESTHeart plugin itself — instantiated directly by services that
 * receive a {@link MongoClient} via injection.
 */
public class DbHelper {

    private static final String USERS_COLLECTION = "users";
    private static final String TEAMS_COLLECTION = "orgs";
    private static final String INVITATIONS_COLLECTION = "auth_invitations";

    /** Duplicate-key error code returned by MongoDB. */
    private static final int DUPLICATE_KEY_CODE = 11000;

    private final MongoClient mclient;
    private final String db;

    public DbHelper(MongoClient mclient, String db) {
        this.mclient = mclient;
        this.db = db;
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
     * Adds a tenant membership to the user's {@code tenants} array using {@code $addToSet}.
     *
     * @param email      the user's email (_id)
     * @param membership a document {@code { id: "...", role: "..." }}
     * @return {@code true} if a document was matched
     */
    public boolean addTenantMembership(String email, BsonDocument membership) {
        var result = users().updateOne(
            eq("_id", new BsonString(email)),
            new BsonDocument("$addToSet", new BsonDocument("tenants", membership))
        );
        return result.getMatchedCount() > 0;
    }

    /**
     * Removes the entry with the given {@code tenantId} from the user's {@code tenants} array.
     *
     * @param email    the user's email (_id)
     * @param tenantId the tenant to remove
     * @return {@code true} if a document was matched
     */
    public boolean removeTenantMembership(String email, BsonValue tenantId) {
        var result = users().updateOne(
            eq("_id", new BsonString(email)),
            Updates.pull("tenants", new BsonDocument("id", tenantId))
        );
        return result.getMatchedCount() > 0;
    }

    /**
     * Updates the role for the given tenant entry in the user's {@code tenants} array.
     *
     * @param email    the user's email (_id)
     * @param tenantId the tenant whose role to change
     * @param newRole  the new role string
     * @return {@code true} if a matching tenant entry was found and modified
     */
    public boolean updateTenantRole(String email, BsonValue tenantId, String newRole) {
        var result = users().updateOne(
            Filters.and(
                eq("_id", new BsonString(email)),
                Filters.eq("tenants.id", tenantId)
            ),
            Updates.set("tenants.$.role", new BsonString(newRole))
        );
        return result.getModifiedCount() > 0;
    }

    /**
     * Sets the user's active tenant (the {@code tenant} field) unconditionally.
     *
     * @param email    the user's email (_id)
     * @param tenantId the tenant to make active
     * @return {@code true} if a document was matched
     */
    public boolean setActiveTenant(String email, BsonValue tenantId) {
        return updateUser(email, new BsonDocument("tenant", tenantId));
    }

    /**
     * Sets the user's active tenant only if the {@code tenant} field is absent or null.
     * Safe to call idempotently after every {@code addTenantMembership} for new users.
     *
     * @param email    the user's email (_id)
     * @param tenantId the tenant to set as active
     * @return {@code true} if the field was set (document matched and had no prior tenant)
     */
    public boolean setActiveTenantIfAbsent(String email, BsonValue tenantId) {
        var result = users().updateOne(
            Filters.and(
                eq("_id", new BsonString(email)),
                Filters.or(
                    Filters.exists("tenant", false),
                    Filters.eq("tenant", null)
                )
            ),
            Updates.set("tenant", tenantId)
        );
        return result.getModifiedCount() > 0;
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
     * Finds a team document by its hex-string {@code _id}.
     *
     * @param teamId the ObjectId hex string
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
     * Removes a member entry from the team's {@code members} array.
     *
     * @param tenantId the team's {@code _id}
     * @param userId   the user to remove
     * @return {@code true} if the team document was matched
     */
    public boolean removeMemberFromTeam(BsonValue tenantId, String userId) {
        var result = teams().updateOne(
            eq("_id", tenantId),
            Updates.pull("members", new BsonDocument("userId", new BsonString(userId)))
        );
        return result.getMatchedCount() > 0;
    }

    /**
     * Updates the role for a member in the team's {@code members} array.
     *
     * @param tenantId the team's {@code _id}
     * @param userId   the member's user id
     * @param newRole  the new role string
     * @return {@code true} if the member entry was found and modified
     */
    public boolean updateMemberRoleInTeam(BsonValue tenantId, String userId, String newRole) {
        var result = teams().updateOne(
            Filters.and(
                eq("_id", tenantId),
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
                      .getCollection(USERS_COLLECTION, BsonDocument.class);
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
    public void createInvitation(String email, String token, BsonValue orgId, String role, long ttlMs, boolean isNewUser) {
        var now = System.currentTimeMillis();
        var doc = new BsonDocument()
                .append("_id", new org.bson.BsonObjectId())
                .append("email", new BsonString(email))
                .append("token", new BsonString(token))
                .append("orgId", orgId)
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
     * Finds the latest pending invitation for a user in a specific org.
     */
    public Optional<BsonDocument> findInvitation(String email, BsonValue orgId) {
        var now = System.currentTimeMillis();
        var doc = invitations().find(
                Filters.and(
                        Filters.eq("email", email),
                        Filters.eq("orgId", orgId),
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
     * Deletes all invitations for a user in a specific org (used after acceptance).
     */
    public void deleteInvitations(String email, BsonValue orgId) {
        invitations().deleteMany(
                Filters.and(
                        Filters.eq("email", email),
                        Filters.eq("orgId", orgId)));
    }
}
