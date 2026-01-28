/*-
 * ========================LICENSE_START=================================
 * restheart-security
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
package org.restheart.security.authorizers;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

import org.restheart.exchange.Request;
import org.restheart.security.ACLRegistry;

public class ACLRegistryImpl implements ACLRegistry {
    private static final ACLRegistryImpl HOLDER = new ACLRegistryImpl();

    private final Set<Predicate<Request<?>>> vetoPredicates;
    private final Set<Predicate<Request<?>>> allowPredicates;
    private final Set<Predicate<Request<?>>> authenticationRequirements;

    private ACLRegistryImpl() {
        vetoPredicates  = new LinkedHashSet<>();
        allowPredicates = new LinkedHashSet<>();
        authenticationRequirements = new LinkedHashSet<>();
    }

    static ACLRegistryImpl getInstance() {
        return HOLDER;
    }

    /**
     * Registers a veto predicate that determines if a request should be denied.
     * When the predicate evaluates to true, the request is immediately forbidden (vetoed).
     * Additionally, a request will also be denied if it is not explicitly authorized by any
     * allow predicates or any other active allowing authorizers.
     *
     * @param veto The veto predicate to register. This predicate should return true to veto (deny) the request,
     *             and false to let the decision be further evaluated by allow predicates or other authorizers.
     */
    @Override
    public void registerVeto(Predicate<Request<?>> veto) {
        this.vetoPredicates.add(veto);
    }

    /**
     * Registers an allow predicate that determines if a request should be authorized.
     * The request is authorized if this predicate evaluates to true, provided that no veto predicates
     * or other active vetoer authorizers subsequently deny the request. This method helps in setting up
     * conditions under which requests can proceed unless explicitly vetoed.
     *
     * @param allow The allow predicate to register. This predicate should return true to authorize the request,
     *              unless it is vetoed by any veto predicates or other vetoing conditions.
     */
    @Override
    public void registerAllow(Predicate<Request<?>> allow) {
        this.allowPredicates.add(allow);
    }

    /**
     * Registers a predicate that determines whether requests handled by the ACLRegistryAllower
     * require authentication. This method is used to specify conditions under which authentication
     * is mandatory. Typically, authentication is required unless there are allow predicates
     * explicitly authorizing requests that are not authenticated.
     *
     *
     * @param authenticationRequired The predicate to determine if authentication is necessary.
     *                               It should return true if the request must be authenticated,
     *                               otherwise false if unauthenticated requests might be allowed.
     */
    @Override
    public void registerAuthenticationRequirement(Predicate<Request<?>> authenticationRequired) {
        this.authenticationRequirements.add(authenticationRequired);
    }

    Set<Predicate<Request<?>>> vetoPredicates() {
        return vetoPredicates;
    }

    Set<Predicate<Request<?>>> allowPredicates() {
        return allowPredicates;
    }

    Set<Predicate<Request<?>>> authenticationRequirements() {
        return authenticationRequirements;
    }
}
