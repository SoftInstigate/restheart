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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.security;

import org.restheart.configuration.ConfigurationException;

/**
 * Registry for programmatically contributing {@link VarResolver}s to {@link AclVarsInterpolator}.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @Inject("acl-vars-registry")
 * private AclVarsRegistry vars;
 *
 * @OnInit
 * public void init() {
 *     vars.register(new SubscriptionVarResolver());
 * }
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 9.7.0
 * @see VarResolver
 */
public interface AclVarsRegistry {
    /**
     * Registers a {@link VarResolver}, making its variable usable in ACL {@code predicate}
     * strings and in {@code readFilter}/{@code mergeRequest} documents.
     *
     * <p>Must be called from {@code @OnInit}, before any request is processed. Built-in
     * resolvers ({@code @user}, {@code @request}, {@code @now}, ...) are registered before any
     * plugin's {@code @OnInit} can run and therefore always win a name collision.
     *
     * @param resolver the resolver to register; must not be {@code null}
     * @throws ConfigurationException if {@link VarResolver#name()} is already registered
     */
    void register(VarResolver resolver) throws ConfigurationException;
}
