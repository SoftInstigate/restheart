/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
package org.restheart.plugins.security;

import java.util.List;

import org.restheart.exchange.Request;
import org.restheart.security.BaseAclPermission;

import io.undertow.util.AttachmentKey;

/**
 * An {@link Authorizer} that can also decide about a {@link RequestDescriptor} — an operation
 * described as data rather than carried by a real exchange (restheart#722).
 *
 * <p>Opt-in, not a default method on {@code Authorizer} itself: an implementation with no notion
 * of path/method-shaped operations (a pure RBAC authorizer, an OAuth-scope authorizer, ...)
 * simply doesn't implement this, and is never consulted for descriptor-based checks — it is never
 * forced to interpret input it cannot meaningfully evaluate, and never gets an answer made up on
 * its behalf.
 */
public interface DescriptorAwareAuthorizer extends Authorizer {

    /**
     * The decisions taken for a request's non-identity operations, in the order
     * {@code Service.operationsToAuthorize()} returned them, attached to the <b>real</b> exchange
     * once every one of them was allowed.
     *
     * <p>This is what carries an ACL's resolved permission across the boundary between deciding
     * and executing. A single-endpoint protocol service (MCP, GraphQL) authorizes an operation it
     * then performs in-process, skipping the whole HTTP interceptor chain — including {@code
     * mongoPermissionFilters} and {@code mongoPermissionProjectResponse}, which are what apply
     * {@code readFilter} and {@code projectResponse} on the REST path. Without the resolved
     * permission travelling with the decision, those two would silently not apply, and a
     * principal restricted to their own rows would read everyone's through {@code /mcp}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    AttachmentKey<List<Decision>> AUTHORIZED_OPERATIONS = (AttachmentKey) AttachmentKey.create(List.class);

    /**
     * Same VETOER/ALLOWER contract as {@link Authorizer#isAllowed(Request)}, evaluated against a
     * descriptor instead of a real exchange.
     *
     * @return the decision, carrying — when allowed — the permission that matched and the request
     *         it was matched against, both {@code null} for an authorizer that resolves no
     *         permissions
     */
    Decision decide(RequestDescriptor descriptor);

    /**
     * The outcome of {@link #decide(RequestDescriptor)}.
     *
     * <p>{@code request} is the request the permission was evaluated against — needed, not just
     * convenient: an ACL filter is written in terms of the caller ({@code @user._id},
     * {@code %ROLES}, {@code @now}, ...) and only resolves against a request that knows who is
     * asking. Interpolating it against anything else would produce a different filter than the
     * REST path's, which is precisely the divergence this mechanism exists to prevent.
     *
     * @param allowed    whether the operation is authorized
     * @param permission the ACL permission that matched, or {@code null}
     * @param request    the request {@code permission} was evaluated against, or {@code null}
     */
    public record Decision(boolean allowed, BaseAclPermission permission, Request<?> request) {

        public static final Decision DENIED = new Decision(false, null, null);

        public static Decision allowed(BaseAclPermission permission, Request<?> request) {
            return new Decision(true, permission, request);
        }
    }
}
