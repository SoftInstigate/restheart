/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins.security;

import java.util.Set;

import org.restheart.exchange.Request;
import org.restheart.security.BaseAclPermission;

/**
 * Every rule that applies to a caller, gathered from the authorizers that can enumerate theirs.
 *
 * <p>Injected under the name {@code acl-permissions}:
 *
 * <pre>
 * &#64;Inject("acl-permissions")
 * private AclPermissions permissions;
 * </pre>
 *
 * <p>What comes back is not an answer, it is the material for one: the caller's rules, to be
 * analysed rather than executed. It is deliberately not exhaustive — an authorizer whose logic is
 * Java contributes nothing and cannot — so a reader must treat the result as "these are the rules
 * I can read", never as "these are all the rules".
 *
 * @see PermissionEnumerator
 */
@FunctionalInterface
public interface AclPermissions {

    /**
     * @param request the live request whose caller the rules are wanted for
     * @return the applicable permissions, in the order the authorizers would consider them
     */
    Set<BaseAclPermission> of(Request<?> request);
}
