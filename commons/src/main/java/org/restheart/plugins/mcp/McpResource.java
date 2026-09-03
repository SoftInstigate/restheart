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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The uniform resource description every {@code McpAware} implementation
 * produces, whether built by the framework's default {@code describeMcp()}
 * (via {@code McpResourceParser}) or by a custom implementation such as
 * {@code MongoService}'s.
 *
 * <p>Immutable once built; use {@link #builder()} to construct one. {@link #toMap()}
 * renders the exact JSON shape returned to MCP clients by {@code list_apis}.
 */
public final class McpResource {
    private final String uri;
    private final String kind;
    private final String description;
    private final List<Map.Entry<Transport, List<String>>> transports;
    private final Map<String, Action> actions;
    private final Map<String, Object> auth;
    private final List<Example> examples;
    private final Map<String, Object> extra;

    private McpResource(Builder b) {
        this.uri = Objects.requireNonNull(b.uri, "uri is required");
        this.kind = b.kind == null ? "service" : b.kind;
        this.description = b.description;
        this.auth = b.auth;
        this.extra = Map.copyOf(b.extra);

        this.actions = new LinkedHashMap<>(b.actions);

        this.transports = new ArrayList<>();
        var declared = b.transportActions.isEmpty() && !this.actions.isEmpty()
                ? Map.of(Transport.HTTP, List.<String>of())
                : b.transportActions;
        for (var entry : declared.entrySet()) {
            var actionNames = entry.getValue().isEmpty()
                    ? List.copyOf(this.actions.keySet())
                    : List.copyOf(entry.getValue());
            this.transports.add(Map.entry(entry.getKey(), actionNames));
        }

        this.examples = List.copyOf(b.examples);
    }

    public String uri() { return uri; }
    public String kind() { return kind; }
    public String description() { return description; }
    public Map<String, Action> actions() { return actions; }
    public List<Example> examples() { return examples; }
    /** Kind-specific top-level fields (e.g. Mongo's {@code pipeline_summary}, {@code warnings}) the generic framework doesn't model. */
    public Map<String, Object> extra() { return extra; }

    /** Transports that carry the given action, in declaration order; empty if the action is unknown to every transport. */
    public List<Transport> transportsFor(String actionName) {
        var result = new ArrayList<Transport>();
        for (var entry : transports) {
            if (entry.getValue().contains(actionName)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public static Builder builder() { return new Builder(); }

    /** Renders the exact JSON shape (as nested {@link Map}/{@link List}/scalars) sent to MCP clients. */
    public Map<String, Object> toMap() {
        var m = new LinkedHashMap<String, Object>();
        m.put("uri", uri);
        m.put("kind", kind);
        if (description != null) {
            m.put("description", description);
        }

        var transportsJson = new ArrayList<Object>();
        for (var entry : transports) {
            var t = new LinkedHashMap<String, Object>();
            t.put("name", entry.getKey().wireName());
            t.put("actions", entry.getValue());
            if (entry.getKey().urlScheme() != null) {
                t.put("url_scheme", entry.getKey().urlScheme());
            }
            if (entry.getKey().mediaType() != null) {
                t.put("media_type", entry.getKey().mediaType());
            }
            transportsJson.add(t);
        }
        m.put("transports", transportsJson);

        var actionsJson = new LinkedHashMap<String, Object>();
        actions.forEach((name, action) -> actionsJson.put(name, action.toMap()));
        m.put("actions", actionsJson);

        if (auth != null) {
            m.put("auth", auth);
        }

        var examplesJson = new ArrayList<Object>();
        examples.forEach(e -> examplesJson.add(e.toMap()));
        m.put("examples", examplesJson);

        // kind-specific fields (e.g. pipeline_summary, warnings) — callers must not use
        // a key already set above; this deliberately doesn't guard against that, since
        // extra() is only ever populated by trusted, in-framework McpResourceBuilder code
        m.putAll(extra);

        return m;
    }

    /** Wire-level transport a resource can be invoked over. */
    public enum Transport {
        HTTP("http", null, null),
        WEBSOCKET("websocket", "wss", null),
        SSE("sse", null, "text/event-stream");

        private final String wireName;
        private final String urlScheme;
        private final String mediaType;

        Transport(String wireName, String urlScheme, String mediaType) {
            this.wireName = wireName;
            this.urlScheme = urlScheme;
            this.mediaType = mediaType;
        }

        public String wireName() { return wireName; }
        public String urlScheme() { return urlScheme; }
        public String mediaType() { return mediaType; }
    }

    /** One invokable action of a resource (e.g. {@code query}, {@code create}, {@code execute}). */
    public static final class Action {
        private String method;
        private String pathTemplate;
        private final Map<String, Param> params = new LinkedHashMap<>();
        private Map<String, Object> bodySchema;
        private String description;

        public Action method(String method) { this.method = method; return this; }
        public Action pathTemplate(String pathTemplate) { this.pathTemplate = pathTemplate; return this; }
        public Action bodySchema(Map<String, Object> bodySchema) { this.bodySchema = bodySchema; return this; }
        public Action description(String description) { this.description = description; return this; }

        public Action param(String name, String type, boolean required) {
            params.put(name, new Param(type, null, required, null, null));
            return this;
        }

        public Action param(String name, Param param) {
            params.put(name, param);
            return this;
        }

        public Map<String, Param> params() { return params; }
        public String method() { return method; }
        public String pathTemplate() { return pathTemplate; }
        public String description() { return description; }
        public Map<String, Object> bodySchema() { return bodySchema; }

        Map<String, Object> toMap() {
            var m = new LinkedHashMap<String, Object>();
            if (method != null) {
                m.put("method", method);
            }
            if (pathTemplate != null) {
                m.put("path_template", pathTemplate);
            }
            if (!params.isEmpty()) {
                var paramsJson = new LinkedHashMap<String, Object>();
                params.forEach((name, p) -> paramsJson.put(name, p.toMap()));
                m.put("params", paramsJson);
            }
            if (bodySchema != null) {
                m.put("body_schema", bodySchema);
            }
            if (description != null) {
                m.put("description", description);
            }
            return m;
        }
    }

    /**
     * One parameter of an {@link Action}. {@code properties} is optional and only meaningful for
     * {@code type: "object"} — documents the shape of its value (e.g. an aggregation's
     * {@code avars} bundle: one MongoDB {@code $var} reference per property) without requiring a
     * full {@code body_schema}, which is for the request body, not a query/path param's value.
     */
    public record Param(String type, String description, boolean required, List<Object> enumValues, Object defaultValue,
            Map<String, Param> properties) {

        public Param(String type, String description, boolean required, List<Object> enumValues, Object defaultValue) {
            this(type, description, required, enumValues, defaultValue, null);
        }

        Map<String, Object> toMap() {
            var m = new LinkedHashMap<String, Object>();
            if (type != null) {
                m.put("type", type);
            }
            if (description != null) {
                m.put("description", description);
            }
            m.put("required", required);
            if (enumValues != null) {
                m.put("enum", enumValues);
            }
            if (defaultValue != null) {
                m.put("default", defaultValue);
            }
            if (properties != null && !properties.isEmpty()) {
                var propsJson = new LinkedHashMap<String, Object>();
                properties.forEach((name, p) -> propsJson.put(name, p.toMap()));
                m.put("properties", propsJson);
            }
            return m;
        }
    }

    /** One curated invocation example, rendered through {@code how_to_call} by the framework. */
    public record Example(String description, String action, Map<String, Object> args) {
        Map<String, Object> toMap() {
            var m = new LinkedHashMap<String, Object>();
            if (description != null) {
                m.put("description", description);
            }
            if (action != null) {
                m.put("action", action);
            }
            m.put("args", args == null ? Map.of() : args);
            return m;
        }
    }

    public static final class Builder {
        private String uri;
        private String kind;
        private String description;
        private Map<String, Object> auth;
        private final Map<String, Action> actions = new LinkedHashMap<>();
        private final Map<Transport, List<String>> transportActions = new LinkedHashMap<>();
        private final List<Example> examples = new ArrayList<>();
        private final Map<String, Object> extra = new LinkedHashMap<>();

        public Builder uri(String uri) { this.uri = uri; return this; }
        public Builder kind(String kind) { this.kind = kind; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder auth(Map<String, Object> auth) { this.auth = auth; return this; }

        public Builder action(String name, Consumer<Action> spec) {
            var action = actions.computeIfAbsent(name, n -> new Action());
            spec.accept(action);
            return this;
        }

        /** Declares support for a transport. With no action names, all actions declared so far (or at build time) apply. */
        public Builder transport(Transport transport, String... actionNames) {
            var set = transportActions.computeIfAbsent(transport, t -> new ArrayList<>());
            set.addAll(new LinkedHashSet<>(List.of(actionNames)));
            return this;
        }

        public Builder example(String description, String action, Map<String, Object> args) {
            examples.add(new Example(description, action, args));
            return this;
        }

        /** Sets a kind-specific top-level field not modeled by the generic framework (see {@link McpResource#extra()}). */
        public Builder extra(String key, Object value) {
            extra.put(key, value);
            return this;
        }

        public McpResource build() {
            return new McpResource(this);
        }
    }
}
