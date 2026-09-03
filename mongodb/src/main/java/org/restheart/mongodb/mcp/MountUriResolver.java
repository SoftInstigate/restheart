/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.restheart.mongodb.MongoServiceConfiguration;

/**
 * Computes the externally-visible URL path of a database/collection given the operator's
 * {@code mongo-mounts} configuration — the reverse of what
 * {@code org.restheart.mongodb.utils.MongoMountResolverImpl} does at request time (URL path
 * &rarr; database/collection). No such reverse lookup exists elsewhere in RESTHeart, so this
 * mirrors {@code MongoMountResolverImpl}'s mount grammar directly against its source rather
 * than guessing:
 *
 * <ul>
 *   <li>{@code what: "*"} (wildcard) &rarr; every database/collection is exposed under
 *       {@code where}, one path segment each: {@code where/db} and {@code where/db/coll}.</li>
 *   <li>{@code what: "db/{*}"} (a single database, collections flattened) &rarr; that database
 *       itself is exposed exactly at {@code where}, its collections at {@code where/coll}
 *       (no {@code db} segment in the URL — this is RESTHeart's default: {@code * -> db
 *       restheart} in {@code MongoMountResolverImpl} terms is actually {@code restheart/{*} ->
 *       /}, so collection {@code restheart.users} is exposed at {@code /users}).</li>
 *   <li>{@code what: "db"} (a single database, no trailing {@code /{*}}) &rarr; same URL shape
 *       as above: database at {@code where}, collections at {@code where/coll}. The two forms
 *       only differ in how {@code MongoMountResolverImpl} interprets write permissions at
 *       request time, not in URL shape.</li>
 *   <li>{@code what: "db/coll"} (a fixed single collection) &rarr; that one collection is
 *       exposed exactly at {@code where}; no distinct database-level URL is implied.</li>
 * </ul>
 *
 * <p>Mounts are tried in configuration order, first {@code what}-match wins. This is <b>not</b>
 * a mirror of RESTHeart's actual request-routing precedence — real dispatch goes through
 * {@code MongoService}'s own {@code PathMatcher}/{@code PathTemplateMatcher} (Undertow,
 * longest-{@code where}-prefix wins; confirmed by reading {@code MongoService.init()}/{@code
 * handle()} — {@code MongoMountResolverImpl}'s own first-match loop only populates supplementary
 * per-request context such as {@code avars} for a mount PathMatcher already chose, it does not
 * choose it). But that routing question ("which mount handles incoming path P") isn't the
 * question this class answers ("what's THE URL for db/collection D/C") — when two mounts with
 * different, non-overlapping {@code where} prefixes both genuinely expose the same D/C (as
 * configured {@code where}s always are in practice; two mounts sharing one {@code where} would
 * just clobber each other in {@code PathMatcher}'s own map), each is independently, correctly
 * routable, so there is no real precedence to mirror — first-{@code what}-match is simply a
 * deterministic, documented convention for picking one canonical URI to show an agent instead of
 * listing every reachable path as a separate catalog entry.
 *
 * <p>A database/collection with no matching mount is not reachable over HTTP at all and resolves
 * to {@link Optional#empty()}; parametric (multi-tenant, {@code {host[0]}}) mounts are not
 * supported here and are simply skipped.
 */
final class MountUriResolver {

    record Mount(String what, String where) {
    }

    private final List<Mount> mounts;

    MountUriResolver(List<Mount> mounts) {
        this.mounts = List.copyOf(mounts);
    }

    static MountUriResolver fromConfig() {
        var raw = MongoServiceConfiguration.get().getMongoMounts();
        return new MountUriResolver(parse(raw));
    }

    private static List<Mount> parse(List<Map<String, Object>> raw) {
        return raw == null
                ? List.of()
                : raw.stream().map(m -> new Mount(String.valueOf(m.get("what")), String.valueOf(m.get("where")))).toList();
    }

    /** @return the database's URL path (e.g. {@code /warehouse}), or empty if no mount exposes it */
    Optional<String> databasePath(String dbName) {
        for (var mount : mounts) {
            if ("*".equals(mount.what())) {
                return Optional.of(join(mount.where(), dbName));
            }

            var resource = stripLeadingSlash(mount.what());
            if (resource.endsWith("/{*}")) {
                if (resource.substring(0, resource.length() - 4).equals(dbName)) {
                    return Optional.of(normalizeRoot(mount.where()));
                }
            } else if (!resource.contains("/") && resource.equals(dbName)) {
                return Optional.of(normalizeRoot(mount.where()));
            }
            // fixed-collection mounts ("db/coll") expose no distinct database-level URL
        }
        return Optional.empty();
    }

    /** @return the collection's URL path (e.g. {@code /warehouse/inventory}), or empty if no mount exposes it */
    Optional<String> collectionPath(String dbName, String collName) {
        for (var mount : mounts) {
            if ("*".equals(mount.what())) {
                return Optional.of(join(mount.where(), dbName, collName));
            }

            var resource = stripLeadingSlash(mount.what());
            if (resource.endsWith("/{*}")) {
                if (resource.substring(0, resource.length() - 4).equals(dbName)) {
                    return Optional.of(join(mount.where(), collName));
                }
            } else if (resource.contains("/")) {
                var parts = resource.split("/", 2);
                if (parts[0].equals(dbName) && parts.length > 1 && parts[1].equals(collName)) {
                    return Optional.of(normalizeRoot(mount.where()));
                }
            } else if (resource.equals(dbName)) {
                return Optional.of(join(mount.where(), collName));
            }
        }
        return Optional.empty();
    }

    private static String stripLeadingSlash(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }

    private static String normalizeRoot(String where) {
        return where.isEmpty() ? "/" : where;
    }

    private static String join(String base, String... segments) {
        var sb = new StringBuilder("/".equals(base) ? "" : stripTrailingSlash(base));
        for (var s : segments) {
            sb.append('/').append(s);
        }
        return sb.toString();
    }

    private static String stripTrailingSlash(String s) {
        return s.length() > 1 && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
