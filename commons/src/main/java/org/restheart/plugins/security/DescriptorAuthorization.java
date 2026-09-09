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

import org.restheart.plugins.security.DescriptorAwareAuthorizer.Decision;

/**
 * Decides a single {@link RequestDescriptor} against the configured
 * {@link DescriptorAwareAuthorizer}s, applying the framework's own rule.
 *
 * <p>Injected under the name {@code descriptor-authorization}:
 *
 * <pre>
 * &#64;Inject("descriptor-authorization")
 * private DescriptorAuthorization authorization;
 * </pre>
 *
 * <p>It exists so that a plugin asking "could this caller perform this operation?" gets the same
 * answer the framework would give when actually performing it. The MCP server asks it once per
 * catalog entry, to leave out of a listing what a read would refuse; the framework asks it per
 * request, to allow or refuse. A plugin restating the rule for itself would be a security boundary
 * that can drift — showing more than an authorizer allows, or hiding what it allows, with nothing
 * failing to say so.
 *
 * @see DescriptorAwareAuthorizer
 */
@FunctionalInterface
public interface DescriptorAuthorization {

    /**
     * Decides one operation.
     *
     * @param descriptor the operation to decide, as the caller would perform it
     * @return the {@link Decision} that granted it — carrying whatever {@code readFilter} or
     *         projection the matched permission resolved — or {@link Decision#DENIED}
     */
    Decision decide(RequestDescriptor descriptor);

    /** Convenience for the common case of only needing to know whether it is allowed. */
    default boolean isAllowed(RequestDescriptor descriptor) {
        return decide(descriptor).allowed();
    }
}
