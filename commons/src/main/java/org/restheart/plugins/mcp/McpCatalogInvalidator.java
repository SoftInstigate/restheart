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
 * Tells the MCP server that a catalogue it may be holding is out of date.
 *
 * <p>The catalogue is cached, and until now only time emptied it. That is fine for what the cache
 * is for — a session's many calls should not each rescan a database — and wrong for the two things
 * that depend on the catalogue being current:
 *
 * <ul>
 * <li>{@code notifications/resources/list_changed} exists so a client learns when the catalogue
 * changes. Fired on expiry alone, it wakes clients at arbitrary moments and stays silent at the one
 * moment it is meant for.
 * <li>Anything that publishes a resource and then shows what an agent would find — a console, a
 * test — reads the answer from before the change, for as long as the window lasts.
 * </ul>
 *
 * <p>Deliberately not a data-source watcher. Nothing here subscribes to anything: whoever changed
 * something says so, once, on the request that changed it. An {@code McpAware} service with no
 * cheap way to know when its own content changed simply does not call this, and time still empties
 * its catalogue as before.
 *
 * <p>Found by type among the registered {@code Provider}s, the same way {@link McpScopeProvider}
 * is, so nothing has to agree on a name. Absent — no MCP server on this instance — there is
 * nothing to invalidate and callers can skip the work.
 *
 * <p><strong>This is node-local, and does not replace the expiry.</strong> Only the instance that
 * handled the write drops anything; where several serve the same scope behind a load balancer, the
 * others keep answering from what they built until their own window closes. The cache TTL stays
 * the guarantee, and this makes the common case — one instance, someone changing something and
 * looking straight at the result — immediate rather than eventual.
 */
public interface McpCatalogInvalidator {

    /**
     * Drops the catalogue of the partition this request belongs to.
     *
     * <p>Takes the request, not a scope: which partition a request belongs to is the scope
     * provider's business, and a caller resolving it for itself would be a second answer to a
     * question that must have one. The next call rebuilds the catalogue, connected clients are
     * told it changed, and no other partition is touched.
     *
     * @param request the request that changed something the catalogue describes
     */
    void invalidate(Request<?> request);
}
