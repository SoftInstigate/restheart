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
package org.restheart.plugins.mcp;

import org.restheart.exchange.Request;

/**
 * Decides which partition of the MCP catalogue serves a request.
 *
 * <p>A <strong>scope</strong> is an opaque partition identifier: a token naming <em>which</em>
 * partition, never <em>what</em> a partition is. What it means is each service's own business —
 * the MongoDB service reads it as a database name, another service maps it onto whatever its own
 * content is organised by. Nothing here knows about databases.
 *
 * <p>One implementation is active per instance, and it is the only thing that decides the
 * partition space. No service resolves the scope for itself: two services disagreeing would
 * produce a catalogue that contradicts itself, advertising one service's resources next to
 * another's from a different partition.
 *
 * <p><strong>Nothing is partitioned unless a deployment says so.</strong> With no implementation
 * registered, every request resolves to {@link #UNPARTITIONED} and the catalogue behaves as it
 * always has: one instance, one catalogue, every database. There is no configuration switch —
 * registering an implementation is the switch.
 *
 * <p>A deployment supplies one as a {@code Provider<McpScopeProvider>}; {@code mcpService} finds
 * it by type, so no name has to be agreed between the two.
 *
 * <p><strong>Three answers, not two.</strong> {@link #UNRESOLVED} exists so that "I should be
 * partitioning this request but cannot tell which partition" is distinguishable from "there is no
 * partitioning here". Folding the first into the second means a provider that fails on some
 * request quietly serves that caller the whole instance — the exact leak partitioning is for.
 */
public interface McpScopeProvider {

    /**
     * No partitioning: the caller sees the whole catalogue.
     *
     * <p>{@code "*"} is already the convention for "everything" in {@code mongo-mounts}, and it
     * cannot collide with a real scope: the tenant-id syntax accepts letters, digits and hyphens
     * only, so no legitimate scope can ever equal it.
     */
    String UNPARTITIONED = "*";

    /**
     * The scope could not be determined, and the request must be refused rather than served the
     * whole catalogue. Unforgeable for the same reason as {@link #UNPARTITIONED}.
     */
    String UNRESOLVED = "?";

    /**
     * The scope this request belongs to. Never {@code null}: return {@link #UNPARTITIONED} to
     * partition nothing, {@link #UNRESOLVED} to refuse.
     *
     * <p>Called once per request to {@code /mcp}, before the session is looked up — the answer
     * chooses which MCP server serves the request, so it cannot depend on anything the server
     * does afterwards.
     *
     * @param request the incoming request
     * @return the scope, {@link #UNPARTITIONED}, or {@link #UNRESOLVED}
     */
    String scopeOf(Request<?> request);
}
