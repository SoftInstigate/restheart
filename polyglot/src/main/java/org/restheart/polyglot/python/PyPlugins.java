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
package org.restheart.polyglot.python;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * Helpers shared by Python services and interceptors.
 *
 * <p>A Python plugin is a module that defines {@code options} (a dict),
 * {@code handle(request, response)} and, for interceptors,
 * {@code resolve(request)}. It is declared in the plugin's package.json under
 * {@code rh:services} / {@code rh:interceptors}, exactly like a JS plugin.</p>
 *
 * <p>The module is imported once per Context via importlib and cached in
 * {@code sys.modules}; the Sources built here evaluate to one of its members,
 * so the rest of the polyglot machinery (ContextQueue, cacheHandleFunction,
 * JSInterceptor.handle/resolve) works unchanged.</p>
 */
public final class PyPlugins {
    public static final String LANGUAGE = "python";

    private PyPlugins() {
    }

    /**
     * @return the parent directory that contains package.json
     */
    public static Path pluginRoot(Path pluginPath) {
        var pluginRoot = pluginPath.getParent();
        while (!Files.exists(pluginRoot.resolve("package.json"))) {
            pluginRoot = pluginRoot.getParent();
        }
        return pluginRoot;
    }

    /**
     * Context options for a Python plugin. If the plugin root contains a
     * virtual environment ({@code .venv} or {@code venv}), the Context uses it,
     * so each plugin has its own isolated set of packages.
     */
    public static Map<String, String> contextOptions(Path pluginRoot) {
        var opts = new HashMap<String, String>();
        opts.put("python.PythonPath", pluginRoot.toAbsolutePath().toString());

        for (var name : new String[] { ".venv", "venv" }) {
            var venvPython = pluginRoot.resolve(name).resolve("bin").resolve("python");
            if (Files.exists(venvPython)) {
                opts.put("python.Executable", venvPython.toAbsolutePath().toString());
                opts.put("python.ForceImportSite", "true");
                break;
            }
        }

        return opts;
    }

    /**
     * @return a Source that imports the plugin module (once per Context) and
     *         evaluates to {@code module.<member>}
     */
    public static Source memberSource(Path pluginPath, String member) {
        var path = pluginPath.toAbsolutePath().toString();
        var moduleName = "rh_plugin_" + Integer.toHexString(path.hashCode());
        var script = """
                def __rh_load(path, name):
                    import sys, importlib.util
                    m = sys.modules.get(name)
                    if m is None:
                        spec = importlib.util.spec_from_file_location(name, path)
                        m = importlib.util.module_from_spec(spec)
                        sys.modules[name] = m
                        try:
                            spec.loader.exec_module(m)
                        except BaseException:
                            del sys.modules[name]
                            raise
                    return m
                getattr(__rh_load(%s, %s), %s, None)
                """.formatted(pyStr(path), pyStr(moduleName), pyStr(member));
        return Source.newBuilder(LANGUAGE, script, pluginPath.getFileName() + ":" + member).buildLiteral();
    }

    /**
     * Converts the module's {@code options} dict to a Java map.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> options(Value options, Path pluginPath) {
        if (options == null || options.isNull() || !options.hasHashEntries()) {
            throw new IllegalArgumentException("wrong python plugin " + pluginPath.toAbsolutePath()
                    + ", the module must define a dict 'options'");
        }
        var ret = new HashMap<String, Object>();
        var keys = options.getHashKeysIterator();
        while (keys.hasIteratorNextElement()) {
            var k = keys.getIteratorNextElement();
            var v = options.getHashValue(k);
            ret.put(k.asString(), v.isString() ? v.asString() : v.isBoolean() ? v.asBoolean() : v.as(Object.class));
        }
        return ret;
    }

    public static String requireString(Map<String, Object> options, String key, Path pluginPath) {
        if (!(options.get(key) instanceof String s)) {
            throw new IllegalArgumentException("wrong python plugin " + pluginPath.toAbsolutePath()
                    + ", missing or wrong member options['" + key + "']");
        }
        return s;
    }

    public static void requireCallable(Value v, String member, Path pluginPath) {
        if (v == null || !v.canExecute()) {
            throw new IllegalArgumentException("wrong python plugin " + pluginPath.toAbsolutePath()
                    + ", the module must define the function '" + member + "'");
        }
    }

    private static String pyStr(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
