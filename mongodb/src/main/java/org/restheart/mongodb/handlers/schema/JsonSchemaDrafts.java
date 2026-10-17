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
package org.restheart.mongodb.handlers.schema;

import java.util.Map;
import java.util.Optional;

import org.bson.BsonString;
import org.bson.BsonValue;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * The JSON Schema drafts the schema store takes: draft-04, draft-06 and draft-07, the ones everit
 * validates. A schema says which with its {@code $schema}; without one it is draft-07.
 *
 * <p>The store used to overwrite {@code $schema} with draft-04 on every write, so a schema written
 * for draft-07 was validated as draft-04 and its {@code if}/{@code then}/{@code else} and
 * {@code const} were ignored without a word (#750).
 */
final class JsonSchemaDrafts {

    /** The draft of a schema that declares none. */
    static final String DEFAULT = "http://json-schema.org/draft-07/schema#";

    private static final Map<String, String> METASCHEMAS = Map.of(
            "http://json-schema.org/draft-04/schema", "json-schema-draft-v4.json",
            "http://json-schema.org/draft-06/schema", "json-schema-draft-v6.json",
            "http://json-schema.org/draft-07/schema", "json-schema-draft-v7.json");

    private static final Map<String, Schema> LOADED = new java.util.concurrent.ConcurrentHashMap<>();

    /** The drafts, as the error of an unsupported {@code $schema} lists them. */
    static final String SUPPORTED = "draft-04, draft-06 or draft-07 (default draft-07: " + DEFAULT + ")";

    private JsonSchemaDrafts() {
    }

    /**
     * The draft a {@code $schema} value names, as the metaschema's URI without the fragment:
     * {@code http} or {@code https}, with or without the trailing {@code #}. Empty when the value
     * names no supported draft; {@link #DEFAULT}'s when there is no value.
     */
    static Optional<String> draftOf(BsonValue $schema) {
        if ($schema == null || $schema.isNull()) {
            return Optional.of(key(DEFAULT));
        }

        if (!($schema instanceof BsonString s)) {
            return Optional.empty();
        }

        var key = key(s.getValue().strip());
        return METASCHEMAS.containsKey(key) ? Optional.of(key) : Optional.empty();
    }

    /** Whether the draft uses {@code $id} rather than draft-04's {@code id}. */
    static boolean usesDollarId(String draft) {
        return !draft.endsWith("draft-04/schema");
    }

    /** The metaschema of a supported draft, loaded once. */
    static Schema metaschema(String draft) {
        return LOADED.computeIfAbsent(draft, d -> {
            try (var in = JsonSchemaDrafts.class.getClassLoader().getResourceAsStream(METASCHEMAS.get(d))) {
                return SchemaLoader.load(new JSONObject(new JSONTokener(in)));
            } catch (java.io.IOException e) {
                throw new IllegalStateException("cannot read the metaschema of " + d, e);
            }
        });
    }

    private static String key(String uri) {
        var noFragment = uri.endsWith("#") ? uri.substring(0, uri.length() - 1) : uri;
        return noFragment.startsWith("https://") ? "http://" + noFragment.substring("https://".length()) : noFragment;
    }
}
