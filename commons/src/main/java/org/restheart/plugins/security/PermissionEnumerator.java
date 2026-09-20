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
 * Implemented by an {@link Authorizer} that can say which of its rules apply to a caller.
 *
 * <p>It promises only that — not a decision. The MCP catalog needs the rules themselves, because
 * what it has to answer is "could some call to this resource be allowed", and a rule that decides
 * on the arguments of a call cannot answer that when there is no call: putting it a request made up
 * for the occasion gets a refusal that the real request would never have got (#743).
 *
 * <p>Only an authorizer whose rules are text can implement this: {@code MongoAclAuthorizer} and
 * {@code FileAclAuthorizer} do. One whose logic is Java has nothing to enumerate, and the catalog
 * does not pretend otherwise — it lists what it cannot rule out, and a resource whose visibility
 * such an authorizer decides says so in its own {@code mcp} metadata.
 */
public interface PermissionEnumerator {

    /**
     * The permissions this authorizer would consider for {@code request}'s caller, in the order it
     * would consider them.
     *
     * @param request the live request the caller is making
     * @return the applicable permissions, possibly empty, never {@code null}
     */
    Set<BaseAclPermission> permissions(Request<?> request);
}
