/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.polyglot.spi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
import org.restheart.polyglot.spi.PluginDefinition.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A plugin module as the runtime reads it: one {@code import * as m} per context, cached in
 * the context bindings, and the declaration parsed from its namespace.
 *
 * <p>Two shapes are accepted. An SDK plugin exports {@code defineService({...})} or
 * {@code defineInterceptor({...})} as its default export. A legacy plugin exports
 * {@code options}, {@code handle} and, for interceptors, {@code resolve}, and keeps working
 * unchanged.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public final class PluginModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginModule.class);

    /** The binding the module namespace is cached in, per context. */
    public static final String MODULE_KEY = "__rhModule";

    private static final Pattern MISSING_EXPORT = Pattern.compile("does not provide an export named '([A-Za-z_$][A-Za-z0-9_$]*)'");

    private PluginModule() {
    }

    /**
     * @param pluginPath the module file
     * @return the source that imports it and evaluates to its namespace
     */
    public static Source source(Path pluginPath) {
        var uri = pluginPath.toAbsolutePath().toUri().toString();
        return Source.newBuilder("js", "import * as m from '" + uri + "'; m;", "plugin:" + pluginPath.getFileName())
                .mimeType("application/javascript+module")
                .buildLiteral();
    }

    /**
     * The namespace of the module in a context, evaluated once per context.
     *
     * @param ctx an entered context
     * @param source the module source of {@link #source(Path)}
     * @return the namespace
     */
    public static Value namespace(Context ctx, Source source) {
        var bindings = ctx.getBindings("js");

        if (bindings.hasMember(MODULE_KEY)) {
            return bindings.getMember(MODULE_KEY);
        }

        var ns = ctx.eval(source);
        bindings.putMember(MODULE_KEY, ns);
        LOGGER.trace("Cached module namespace in context");
        return ns;
    }

    /**
     * @param ns the namespace
     * @return whether the module is an SDK plugin: a default export made by {@code defineService} or {@code defineInterceptor}
     */
    public static boolean isSdk(Value ns) {
        var def = ns.getMember("default");
        return def != null && def.hasMembers() && def.hasMember("__sdk") && def.getMember("__sdk").isBoolean() && def.getMember("__sdk").asBoolean();
    }

    /**
     * @param ns the namespace
     * @return the object with the declaration: the default export of an SDK plugin, {@code options} of a legacy one
     */
    public static Value options(Value ns) {
        return isSdk(ns) ? ns.getMember("default") : ns.getMember("options");
    }

    /**
     * @param ns the namespace
     * @return the {@code handle} function
     */
    public static Value handle(Value ns) {
        return isSdk(ns) ? ns.getMember("default").getMember("handle") : ns.getMember("handle");
    }

    /**
     * @param ns the namespace
     * @return the {@code resolve} function, null when the module has none
     */
    public static Value resolve(Value ns) {
        var r = isSdk(ns) ? ns.getMember("default").getMember("resolve") : ns.getMember("resolve");
        return r != null && r.canExecute() ? r : null;
    }

    /**
     * The plugin directory, the closest ancestor with a {@code package.json}.
     *
     * @param pluginPath the module file
     * @return the directory
     */
    public static Path pluginRoot(Path pluginPath) {
        var dir = pluginPath.toAbsolutePath().getParent();

        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("package.json"))) {
                return dir;
            }
            dir = dir.getParent();
        }

        return pluginPath.toAbsolutePath().getParent();
    }

    /**
     * The context options a plugin needs: {@code require} with its {@code node_modules} as the
     * cwd, when the plugin directory has one.
     *
     * @param pluginRoot the plugin directory
     * @return the options
     */
    public static Map<String, String> contextOptions(Path pluginRoot) {
        var opts = new HashMap<String, String>();
        var nodeModules = pluginRoot.resolve("node_modules");

        if (Files.isDirectory(nodeModules)) {
            opts.put("js.commonjs-require", "true");
            opts.put("js.commonjs-require-cwd", nodeModules.toAbsolutePath().toString());
        }

        return opts;
    }

    /**
     * Reads and checks the declaration of a module.
     *
     * @param kind what {@code package.json} lists the module as
     * @param ns the namespace
     * @param pluginPath the module file
     * @param pluginRoot the plugin directory
     * @param contextOptions the context options
     * @return the definition
     * @throws IllegalArgumentException when the declaration is wrong, with what is wrong
     */
    public static PluginDefinition define(Kind kind, Value ns, Path pluginPath, Path pluginRoot, Map<String, String> contextOptions) {
        var sdk = isSdk(ns);
        var options = options(ns);
        var where = (kind == Kind.SERVICE ? "wrong js service " : "wrong js interceptor ") + pluginPath.toAbsolutePath();

        if (options == null || options.isNull() || !options.hasMembers()) {
            throw new IllegalArgumentException(where + ": " + (kind == Kind.SERVICE ? SERVICE_HINT : INTERCEPTOR_HINT));
        }

        if (sdk) {
            var declared = options.getMember("__kind").asString();
            var expected = kind == Kind.SERVICE ? "service" : "interceptor";
            if (!expected.equals(declared)) {
                throw new IllegalArgumentException(where + ": declared with define" + capitalize(declared)
                        + "() but listed in package.json as " + (kind == Kind.SERVICE ? "rh:services" : "rh:interceptors"));
            }
        }

        var name = string(options, "name", where, true);
        var description = string(options, "description", where, true);

        var handle = handle(ns);
        if (handle == null || !handle.canExecute()) {
            throw new IllegalArgumentException(where + ": missing the function 'handle(req, res)'");
        }

        var contentKind = contentKind(options, kind, where);
        var pluginClass = options.hasMember("pluginClass") ? string(options, "pluginClass", where, false) : null;

        if (kind == Kind.SERVICE) {
            var uri = string(options, "uri", where, true);
            if (!uri.startsWith("/")) {
                throw new IllegalArgumentException(where + ": 'uri' must start with /");
            }

            var secured = false;
            if (options.hasMember("secured")) {
                var s = options.getMember("secured");
                if (!s.isBoolean()) {
                    throw new IllegalArgumentException(where + ": 'secured' must be a boolean");
                }
                secured = s.asBoolean();
            }

            var matchPolicy = MATCH_POLICY.PREFIX;
            if (options.hasMember("matchPolicy")) {
                try {
                    matchPolicy = MATCH_POLICY.valueOf(string(options, "matchPolicy", where, true));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(where + ": 'matchPolicy' must be PREFIX or EXACT");
                }
            }

            if (!contentKind.forServices()) {
                throw new IllegalArgumentException(where + ": contentKind '" + contentKind.key() + "' is for interceptors; a service is string, json, bson or bytes");
            }

            return new PluginDefinition(kind, sdk, name, description, uri, secured, matchPolicy, null, contentKind, pluginClass, pluginPath, pluginRoot, contextOptions);
        }

        var resolve = resolve(ns);
        if (resolve == null) {
            throw new IllegalArgumentException(where + ": missing the function 'resolve(req, res)'");
        }

        var interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH;
        if (options.hasMember("interceptPoint")) {
            try {
                interceptPoint = InterceptPoint.valueOf(string(options, "interceptPoint", where, true));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(where + ": 'interceptPoint' must be one of REQUEST_BEFORE_EXCHANGE_INIT, REQUEST_BEFORE_AUTH, REQUEST_AFTER_AUTH, RESPONSE, RESPONSE_ASYNC");
            }
        }

        return new PluginDefinition(kind, sdk, name, description, null, false, null, interceptPoint, contentKind, pluginClass, pluginPath, pluginRoot, contextOptions);
    }

    private static ContentKind contentKind(Value options, Kind kind, String where) {
        if (options.hasMember("contentKind")) {
            var ck = string(options, "contentKind", where, true);
            return ContentKind.of(ck).orElseThrow(() -> new IllegalArgumentException(where + ": unknown contentKind '" + ck + "', one of string, json, bson, bytes"
                    + (kind == Kind.INTERCEPTOR ? ", mongo, csv" : "")));
        }

        if (options.hasMember("pluginClass")) {
            var pc = string(options, "pluginClass", where, true);
            return ContentKind.ofPluginClass(pc).orElseThrow(() -> new IllegalArgumentException(where + ": unknown pluginClass '" + pc + "'"));
        }

        return ContentKind.STRING;
    }

    private static String string(Value options, String member, String where, boolean required) {
        if (!options.hasMember(member)) {
            if (required) {
                throw new IllegalArgumentException(where + ": missing '" + member + "'");
            }
            return null;
        }

        var v = options.getMember(member);

        if (v == null || !v.isString()) {
            throw new IllegalArgumentException(where + ": '" + member + "' must be a string");
        }

        return v.asString();
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Whether a load error is an import of a capability the deployment does not have.
     *
     * @param e the error of evaluating the module
     * @param capabilities what the deployment has
     * @return the missing capability, empty when the error is something else
     */
    public static Optional<MissingCapability> missingCapability(PolyglotException e, Capabilities capabilities) {
        var message = e.getMessage();

        if (message == null) {
            return Optional.empty();
        }

        var m = MISSING_EXPORT.matcher(message);

        if (m.find()) {
            var name = m.group(1);
            if (Capabilities.isCapability(name)) {
                return Optional.of(new MissingCapability(name, capabilities.unavailableReason(name).orElse("not available")));
            }
            return Optional.of(new MissingCapability(name, "restheart:sdk has no export named '" + name + "'"));
        }

        return Optional.empty();
    }

    /**
     * A capability a plugin imports and the deployment does not have.
     *
     * @param name the capability
     * @param reason why it is not available
     */
    public record MissingCapability(String name, String reason) {
    }

    static final String SERVICE_HINT = """
            the module must export a service, either as the default export
                import { defineService } from "restheart:sdk";
                export default defineService({
                    name: "hello",
                    description: "a fancy description",
                    uri: "/hello",
                    contentKind: "json",
                    handle(req, res) { res.json({ msg: "hello" }); }
                });
            or, the legacy way, as 'options' and 'handle'
                export const options = { name: "hello", description: "...", uri: "/hello" };
                export function handle(req, res) { ... }
            """;

    static final String INTERCEPTOR_HINT = """
            the module must export an interceptor, either as the default export
                import { defineInterceptor } from "restheart:sdk";
                export default defineInterceptor({
                    name: "helloInterceptor",
                    description: "a fancy description",
                    interceptPoint: "RESPONSE",
                    contentKind: "json",
                    handle(req, res) { ... },
                    resolve(req, res) { return req.isHandledBy("hello"); }
                });
            or, the legacy way, as 'options', 'handle' and 'resolve'
                export const options = { name: "helloInterceptor", description: "...", interceptPoint: "RESPONSE" };
                export function handle(req, res) { ... }
                export function resolve(req) { ... }
            """;
}
