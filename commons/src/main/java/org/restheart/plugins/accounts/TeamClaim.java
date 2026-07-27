package org.restheart.plugins.accounts;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

/**
 * Builds the {@code team} JWT claim value shared by every restheart-accounts token issuer.
 *
 * <p>The claim mirrors the {@code user.team} document: a {@code { _id, role }} object carrying
 * the active team's id <em>and</em> the caller's role in it. This is what makes the active
 * team role reachable from ACL rules as {@code @user.team.role} (predicate) and the team id
 * as {@code @user.team._id} ({@code readFilter}/{@code writeFilter}), on every token path —
 * including the core {@code POST /token} login, which copies {@code user.team} verbatim via
 * {@code account-properties-claims: [team]}.
 *
 * <p>{@link org.restheart.accounts.util.JwtHelper} renders the {@code _id} as
 * {@code { "$oid": "<hex>" }} (same representation the scalar {@code team} claim had before
 * 9.6.0), so filters migrate from {@code @user.team} to {@code @user.team._id} with no value change.
 */
public final class TeamClaim {

    private TeamClaim() {
        // utility class — no instances
    }

    /**
     * Builds the {@code { _id, role }} team-claim document.
     *
     * @param teamId the active team id (never {@code null})
     * @param role   the caller's role in that team; omitted from the document when {@code null}
     * @return a {@link BsonDocument} usable both as a JWT extra-claim value and as a response body field
     */
    public static BsonDocument of(BsonValue teamId, String role) {
        var doc = new BsonDocument("_id", teamId);
        if (role != null) {
            doc.append("role", new BsonString(role));
        }
        return doc;
    }
}
