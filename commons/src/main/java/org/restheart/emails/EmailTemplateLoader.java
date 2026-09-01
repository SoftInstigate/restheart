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
package org.restheart.emails;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads email templates from the filesystem or from bundled classpath resources.
 *
 * <p>Originally part of {@code restheart-accounts}; moved to {@code restheart-commons} when
 * {@code restheart-stripe} needed the same resolution for its own billing-notification
 * templates. A second module needing this logic is the point at which it stops being an
 * accounts detail — copying the class instead would leave two implementations of a
 * security-relevant path resolution to keep in step, which is how one of them ends up with
 * a fix the other never gets.
 *
 * <h2>Resolution order</h2>
 * <ol>
 *   <li>If {@code pathOrResource} is an absolute path that exists → load from file system.</li>
 *   <li>If it is a relative path that resolves against the JVM working directory → load
 *       from file system.</li>
 *   <li>Otherwise (or if the file is not found) → load from classpath under
 *       {@code email-templates/<name>} — resolved against whichever module's classpath
 *       resources actually contain it (each module bundles its own built-ins).</li>
 * </ol>
 *
 * <h2>Caching</h2>
 * File-system templates are cached in memory after the first load. Pass {@code true} to
 * {@link #load(String, String, boolean)} to force a reload (useful in development).
 */
public final class EmailTemplateLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailTemplateLoader.class);
    private static final String CLASSPATH_PREFIX = "email-templates/";
    private static final String PACKAGE = EmailTemplateLoader.class.getPackageName();

    /** Simple in-memory cache: resolved path/resource name → content. */
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private EmailTemplateLoader() {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads a template (cached).
     *
     * @param pathOrResource path to an HTML file on disk, or {@code null} / empty
     *                       string to use the built-in classpath resource
     * @param builtinName    name of the bundled resource (without the
     *                       {@code email-templates/} prefix), e.g.
     *                       {@code "verification.html"}
     * @return the raw template content
     * @throws IOException if neither the file nor the classpath resource can be read
     */
    public static String load(String pathOrResource, String builtinName) throws IOException {
        return load(pathOrResource, builtinName, false);
    }

    /**
     * Loads a template from an inline HTML string (e.g. from a MongoDB document),
     * bypassing the filesystem and classpath entirely.
     *
     * <p>The string is cached under a synthetic key derived from its hash,
     * so repeated calls with identical content hit the cache.
     *
     * @param htmlContent the full HTML template string
     * @return the template content (same as input)
     */
    public static String loadInline(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) return htmlContent;
        var cacheKey = "inline:" + Integer.toHexString(htmlContent.hashCode());
        return CACHE.computeIfAbsent(cacheKey, k -> htmlContent);
    }

    /**
     * Loads a template, preferring an inline HTML string when available.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If {@code inlineHtml} is non-blank → use it directly (e.g. a per-tenant override
     *       held in MongoDB).</li>
     *   <li>Otherwise → delegate to {@link #load(String, String)} (file path or built-in).</li>
     * </ol>
     *
     * @param inlineHtml     optional HTML string from a per-tenant configuration document
     * @param pathOrResource optional file-system path (may be null)
     * @param builtinName    fallback built-in resource name, e.g. {@code "verification.html"}
     * @return the raw template content
     * @throws IOException if the file/classpath resource cannot be read
     */
    public static String loadWithFallback(String inlineHtml, String pathOrResource,
                                          String builtinName) throws IOException {
        if (inlineHtml != null && !inlineHtml.isBlank()) {
            return loadInline(inlineHtml);
        }
        return load(pathOrResource, builtinName);
    }

    /**
     *
     * @param pathOrResource path to an HTML file on disk, or {@code null} / empty to
     *                       use the built-in resource
     * @param builtinName    name of the bundled resource (e.g. {@code "invite.html"})
     * @param forceReload    if {@code true} skip the in-memory cache
     * @return the raw template content
     * @throws IOException if the template cannot be loaded from any source
     */
    public static String load(String pathOrResource,
                              String builtinName,
                              boolean forceReload) throws IOException {
        var cacheKey = (pathOrResource != null && !pathOrResource.isBlank())
                ? pathOrResource
                : "classpath:" + builtinName;

        if (!forceReload) {
            var cached = CACHE.get(cacheKey);
            if (cached != null) return cached;
        }

        String content;
        if (pathOrResource != null && !pathOrResource.isBlank()) {
            content = loadFromFile(pathOrResource, builtinName);
        } else {
            content = loadFromClasspath(builtinName);
        }

        CACHE.put(cacheKey, content);
        return content;
    }

    /**
     * Loads a built-in (classpath) template directly, bypassing any file-system path.
     *
     * @param builtinName the resource name, e.g. {@code "verification.html"}
     * @return the template content
     * @throws IOException if the resource is missing from the classpath
     */
    public static String loadBuiltin(String builtinName) throws IOException {
        return loadFromClasspath(builtinName);
    }

    /** Clears the in-memory cache (useful for testing). */
    public static void clearCache() {
        CACHE.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private static String loadFromFile(String pathStr, String builtinName) throws IOException {
        var path = Path.of(pathStr);
        LOGGER.info("Attempting to load email template: path='{}', resolved='{}', exists={}",
                pathStr, path.toAbsolutePath(), Files.exists(path));
        if (Files.exists(path)) {
            var content = Files.readString(path, StandardCharsets.UTF_8);
            LOGGER.info("Loaded custom email template from: {} ({} bytes)", path.toAbsolutePath(), content.length());
            return content;
        }
        // File path configured but file not found — fall back to built-in and warn
        LOGGER.warn("Email template file not found: {} — falling back to built-in '{}'",
                path.toAbsolutePath(), builtinName);
        return loadFromClasspath(builtinName);
    }

    private static String loadFromClasspath(String name) throws IOException {
        var resourceName = CLASSPATH_PREFIX + name;

        for (var cl : classloaders()) {
            if (cl == null) {
                continue;
            }
            try (InputStream is = cl.getResourceAsStream(resourceName)) {
                if (is != null) {
                    LOGGER.debug("Loading built-in email template: {} via {}", resourceName, cl);
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }

        throw new IOException("Built-in email template not found on classpath: " + resourceName);
    }

    /**
     * Where to look for a built-in, in order: the calling module, the thread's
     * context, then this one.
     *
     * <p>The calling module comes first because that is where the resource is:
     * each module bundles its own built-ins — {@code accounts} ships
     * {@code verification.html}, {@code stripe} ships {@code order-confirmed.html}
     * — and this class lives in {@code commons}, which ships none.
     *
     * <p>Asking {@code EmailTemplateLoader.class.getClassLoader()} therefore
     * cannot work once a plugin is loaded in its own classloader, which is how
     * RESTHeart loads every one of them. It looked right and failed everywhere:
     * every verification, invitation and password-reset email was refused a
     * template, and because sending is best-effort the caller logs a warning and
     * carries on — registration answers {@code 201}, no email is sent, and
     * nothing else says so.
     */
    private static List<ClassLoader> classloaders() {
        var loaders = new ArrayList<ClassLoader>(3);

        // The first frame outside this package: the module that asked for the
        // template, and so the one whose jar contains it. `EmailRenderer` is in
        // here too, which is why the test is on the package and not the class.
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(c -> !PACKAGE.equals(c.getPackageName()))
                        .findFirst())
                .map(Class::getClassLoader)
                .ifPresent(loaders::add);

        loaders.add(Thread.currentThread().getContextClassLoader());
        loaders.add(EmailTemplateLoader.class.getClassLoader());

        return loaders;
    }
}
