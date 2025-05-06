/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.security;

import java.util.function.Predicate;

import org.restheart.exchange.Request;

/**
 * Registry for defining Access Control Lists (ACLs) programmatically.
 *
 * This registry is utilized by the {@code ACLRegistryVetoer} and {@code ACLRegistryAllower} authorizers
 * to manage request permissions. The {@code ACLRegistryVetoer} denies requests based on veto predicates,
 * while the {@code ACLRegistryAllower} grants permission to proceed with requests based on allow predicate.
 *
 * A request is permitted to proceed if it is not denied by any {@code ACLRegistryVetoer} and at least one
 * {@code ACLRegistryAllower} approves it.
 *
 * Example usage:
 * <pre>
 * {@code
 * @Inject("acl-registry")
 * ACLRegistry registry;
 *
 * @OnInit
 * public void init() {
 *  registry.registerVeto(request -> request.getPath().equals("/deny"));
 *  registry.registerAllow(request -> request.getPath().equals("/allow"));
 * }
 * }
 * </pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface ACLRegistry {
    /**
     * Registers a veto predicate that determines if a request should be denied.
     * When the predicate evaluates to true, the request is immediately forbidden (vetoed).
     * Additionally, a request will also be denied if it is not explicitly authorized by any
     * allow predicates or any other active allowing authorizers.
     *
     * @param veto The veto predicate to register. This predicate should return true to veto (deny) the request,
     *             and false to let the decision be further evaluated by allow predicates or other authorizers.
     */
    public void registerVeto(Predicate<Request<?>> veto);

    /**
     * Registers an allow predicate that determines if a request should be authorized.
     * The request is authorized if this predicate evaluates to true, provided that no veto predicates
     * or other active vetoer authorizers subsequently deny the request. This method helps in setting up
     * conditions under which requests can proceed unless explicitly vetoed.
     *
     * @param allow The allow predicate to register. This predicate should return true to authorize the request,
     *              unless it is vetoed by any veto predicates or other vetoing conditions.
     */
    public void registerAllow(Predicate<Request<?>> allow);

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
    public void registerAuthenticationRequirement(Predicate<Request<?>> authenticationRequired);

}
