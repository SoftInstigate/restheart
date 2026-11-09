package org.restheart.accounts;

import java.util.Arrays;
import java.util.Deque;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.authenticators.MongoRealmAuthenticator;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;

/**
 * Refuses a request whose {@code filter} or {@code sort} query parameter refers to a one-time
 * token of restheart-accounts, as {@code denyFilterOnUserPwd} does for the password:
 *
 * <ul>
 *   <li>{@code token} in the auth_invitations collection, which activates the invited account;</li>
 *   <li>{@code emailVerificationToken}, {@code passwordResetToken} and {@code inviteToken} in the
 *       users collection of {@code mongoRealmAuthenticator} ({@code users} when it is not enabled),
 *       which verify the address, set a new password and, in the legacy field, activate an invitation.</li>
 * </ul>
 *
 * <p>A permission that lets a role read these collections hides the tokens with
 * {@code projectResponse}, but a condition on the field would still tell, request by request,
 * something about its value. The check runs before the query, on the whole filter: nested
 * documents, arrays, and string values that name the field in an expression.</p>
 *
 * <p>It applies to collections with those names in any database: the accounts database can be
 * overridden per request, and a condition on a token is never needed. It is enabled by default,
 * as {@code denyFilterOnUserPwd}.</p>
 *
 * @since 9.15.3
 */
@RegisterPlugin(name = "denyFilterOnAccountTokens",
        description = "forbids requests with a filter or a sort on the one-time tokens of restheart-accounts",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
public class DenyFilterOnAccountTokens implements MongoInterceptor {
    static final String INVITATIONS_COLLECTION = "auth_invitations";
    static final Set<String> INVITATION_TOKENS = Set.of("token");
    static final Set<String> USER_TOKENS = Set.of("emailVerificationToken", "passwordResetToken", "inviteToken");

    @Inject("registry")
    private PluginsRegistry registry;

    private String usersCollection = "users";

    @OnInit
    public void init() {
        try {
            var pr = registry.getAuthenticator("mongoRealmAuthenticator");

            if (pr != null && pr.isEnabled() && pr.getInstance() instanceof MongoRealmAuthenticator mra && mra.getUsersCollection() != null) {
                this.usersCollection = mra.getUsersCollection();
            }
        } catch (ConfigurationException ce) {
            // no mongoRealmAuthenticator: the users collection keeps its default name
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        var fields = tokensOf(request.getCollectionName());

        return fields != null && (refersTo(request.getFilter(), fields) || refersTo(request.getSortBy(), fields));
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        response.setInError(HttpStatus.SC_FORBIDDEN, "Using filters or sort on a token property is forbidden");
    }

    /**
     * @return the token fields of {@code collection}, or null if it holds none
     */
    private Set<String> tokensOf(String collection) {
        if (INVITATIONS_COLLECTION.equals(collection)) {
            return INVITATION_TOKENS;
        } else if (usersCollection.equals(collection)) {
            return USER_TOKENS;
        } else {
            return null;
        }
    }

    private static boolean refersTo(Deque<String> params, Set<String> fields) {
        if (params == null || params.isEmpty()) {
            return false;
        }

        return params.stream().anyMatch(p -> {
            try {
                return refersTo(BsonUtils.parse(p), fields);
            } catch (Throwable t) {
                // not JSON, e.g. a sort like "token" or "-token": field names, comma separated
                return Arrays.stream(p.split(",")).map(String::strip)
                        .map(name -> name.startsWith("-") || name.startsWith("+") ? name.substring(1) : name)
                        .anyMatch(name -> isOneOf(name, fields));
            }
        });
    }

    /**
     * @return true if {@code value} has a key that is one of {@code fields}, or a path under it,
     *         or a string that refers to it in an expression ({@code "$token"})
     */
    static boolean refersTo(BsonValue value, Set<String> fields) {
        if (value == null) {
            return false;
        }

        if (value.isDocument()) {
            BsonDocument doc = value.asDocument();
            return doc.keySet().stream().anyMatch(key -> isOneOf(key, fields))
                    || doc.values().stream().anyMatch(v -> refersTo(v, fields));
        } else if (value.isArray()) {
            return value.asArray().stream().anyMatch(v -> refersTo(v, fields));
        } else if (value.isString()) {
            var s = value.asString().getValue();
            return s.startsWith("$") && isOneOf(s.substring(1), fields);
        }

        return false;
    }

    private static boolean isOneOf(String name, Set<String> fields) {
        return fields.stream().anyMatch(f -> f.equals(name) || name.startsWith(f + "."));
    }
}
