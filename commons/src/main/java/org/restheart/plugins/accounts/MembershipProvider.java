package org.restheart.plugins.accounts;

import java.util.List;
import java.util.Optional;
import org.bson.BsonValue;

/**
 * Service Provider Interface (SPI) for membership management in restheart-accounts.
 *
 * <p>Implementations control how teams/tenants are created, how users are associated
 * with them, and how active memberships are read and switched. The plugin consults
 * this interface for every membership read/write operation.
 *
 * <p>A default implementation ({@code DefaultMembershipProvider}) is provided in the
 * {@code restheart-accounts} module and preserves the built-in {@code tenant}/{@code tenants}
 * schema. Custom implementations can be registered at startup via:
 *
 * <pre>{@code
 * @RegisterPlugin(name = "myMembershipProvider", description = "...")
 * public class MyMembershipProvider implements MembershipProvider, Initializer {
 *     @Inject("accountsService") private MembershipProviderRegistry accountsService;
 *
 *     @Override
 *     public void init() {
 *         accountsService.registerMembershipProvider(this);
 *     }
 *     // ... implement methods ...
 * }
 * }</pre>
 *
 * <p>If no custom provider is registered, the plugin falls back to the built-in
 * {@code DefaultMembershipProvider}.
 */
public interface MembershipProvider {

    /**
     * Creates an initial team for a newly registered user and assigns them the owner role.
     * Called during {@code /auth/register} and the OAuth new-user path.
     *
     * <p>The provider is responsible for creating the team/tenant storage record and
     * linking the user to it (e.g. updating the user document or a separate membership
     * collection). The returned {@link TenantRef} is used to embed the tenant identifier
     * in the JWT.
     *
     * @param userId   the user's identifier (email address)
     * @param teamName the display name for the new team / tenant
     * @return a {@link TenantRef} carrying the new tenant's ID and display name
     */
    TenantRef createInitialTeam(String userId, String teamName);

    /**
     * Returns {@code true} if the user is already a member of the given tenant.
     * Used by {@code /auth/invite} to short-circuit duplicate invitations.
     *
     * @param userId   the user's identifier
     * @param tenantId the tenant identifier
     * @return {@code true} if the user belongs to the given tenant
     */
    boolean isMember(String userId, BsonValue tenantId);

    /**
     * Adds a user to a tenant with the specified role (operation must be idempotent).
     * Used by {@code /auth/invite} both for new and for existing users.
     *
     * <p>If the user has no active tenant yet, the implementation should also set
     * this tenant as the active one.
     *
     * @param userId   the user's identifier
     * @param tenantId the tenant identifier
     * @param role     the role to assign (e.g. {@code "admin"} or the configured member role)
     */
    void addMember(String userId, BsonValue tenantId, String role);

    /**
     * Returns the user's currently active membership, or {@link Optional#empty()} if
     * the user has no active tenant. Consumed by JWT issuance.
     *
     * @param userId the user's identifier
     * @return an {@link Optional} containing the active {@link Membership}, or empty
     */
    Optional<Membership> activeMembership(String userId);

    /**
     * Returns all memberships for the user. Used by {@code GET /auth/tenants}.
     *
     * @param userId the user's identifier
     * @return a (possibly empty) list of memberships
     */
    List<Membership> listMemberships(String userId);

    /**
     * Sets the given tenant as the user's active membership. Used by
     * {@code POST /auth/switch-tenant}.
     *
     * <p>Implementations must verify that the user is a member before switching.
     * Throws {@link IllegalArgumentException} if {@code tenantId} is not found in
     * the user's membership list.
     *
     * @param userId   the user's identifier
     * @param tenantId the tenant to activate
     * @throws IllegalArgumentException if the user does not belong to the tenant
     */
    void setActiveMembership(String userId, BsonValue tenantId);

    /**
     * Removes a user from the given tenant. Called by {@code DELETE /auth/remove-member}.
     *
     * <p>Implementations must remove the membership entry from both the user-side
     * ({@code user.tenants[]}) and the team-side ({@code team.members[]}) stores.
     * If the tenant being removed is currently the user's active tenant, implementations
     * should clear or update the active tenant field accordingly.
     *
     * <p>This method is a no-op if the user is not a member of the given tenant.
     *
     * @param userId   the user's identifier (email address)
     * @param tenantId the tenant to remove the user from
     */
    default void removeMember(String userId, BsonValue tenantId) {
        throw new UnsupportedOperationException("removeMember not implemented by this provider");
    }

    /**
     * Updates the org-level role of a user within the given tenant.
     * Called by {@code PATCH /auth/member-role}.
     *
     * <p>Implementations must update the role on both the user-side
     * ({@code user.tenants[].role}) and the team-side ({@code team.members[].role}).
     *
     * <p>This method is a no-op if the user is not a member of the given tenant.
     *
     * @param userId   the user's identifier (email address)
     * @param tenantId the tenant in which to update the role
     * @param newRole  the new role to assign (e.g. {@code "admin"} or the configured member role)
     */
    default void updateMemberRole(String userId, BsonValue tenantId, String newRole) {
        throw new UnsupportedOperationException("updateMemberRole not implemented by this provider");
    }

    /**
     * Called by the OAuth callback when the OAuth-authenticated user has
     * {@code status: "invited"}. Implementations should activate the pending
     * invite, record consents if provided, and return the membership to use
     * for JWT issuance.
     *
     * <p>Return {@link Optional#empty()} to fall back to the default
     * behaviour (redirect to {@code frontendErrorUrl}). The default implementation
     * returns empty — preserving the 9.4.1 behaviour of blocking invited users
     * from OAuth login.
     *
     * @param userId   the user's email / identifier
     * @param consents consent versions collected before the OAuth redirect,
     *                 or {@code null} if {@code consentsAccepted=true} was not
     *                 passed through the OAuth state
     * @return an {@link Optional} containing the active
     *         {@link Membership} to embed in the JWT, or empty to redirect to the error URL
     */
    default Optional<Membership> activateViaOAuth(String userId, ConsentRecord consents) {
        return Optional.empty();
    }

}
