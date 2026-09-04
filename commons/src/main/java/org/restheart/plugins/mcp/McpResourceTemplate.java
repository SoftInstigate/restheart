/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.plugins.mcp;

/**
 * An RFC 6570 URI template a {@link McpAware} implementation advertises for a whole class of
 * resources it can produce (e.g. every MongoDB collection reachable under a given {@code
 * mongo-mounts} entry), as opposed to {@link McpResource}, which describes one concrete resource.
 *
 * <p>A template only advertises URI <em>shape</em> — it never bypasses the per-resource opt-in
 * requirement: a URI matching a template's shape still has to resolve to an actual, currently
 * {@code mcp.enabled} resource when read.
 *
 * @param uriTemplate the RFC 6570 template, e.g. {@code https://host/{collection}/_aggrs/{name}}
 * @param name a stable identifier, unique among this plugin's templates
 * @param title short human-readable label
 * @param description longer explanation of what the template matches
 */
public record McpResourceTemplate(String uriTemplate, String name, String title, String description) {
    public McpResourceTemplate(String uriTemplate, String name, String title) {
        this(uriTemplate, name, title, null);
    }
}
