package org.restheart.accounts.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads email templates from the filesystem or from bundled classpath resources.
 *
 * <h2>Resolution order</h2>
 * <ol>
 *   <li>If {@code pathOrResource} is an absolute path that exists → load from file system.</li>
 *   <li>If it is a relative path that resolves against the JVM working directory → load
 *       from file system.</li>
 *   <li>Otherwise (or if the file is not found) → load from classpath under
 *       {@code email-templates/<name>}.</li>
 * </ol>
 *
 * <h2>Caching</h2>
 * File-system templates are cached in memory after the first load. Pass {@code true} to
 * {@link #load(String, boolean)} to force a reload (useful in development).
 */
public final class EmailTemplateLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailTemplateLoader.class);
    private static final String CLASSPATH_PREFIX = "email-templates/";

    /** Simple in-memory cache: resolved path/resource name → content. */
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private EmailTemplateLoader() {}

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
     *   <li>If {@code inlineHtml} is non-blank → use it directly (MongoDB per-tenant template).</li>
     *   <li>Otherwise → delegate to {@link #load(String, String)} (file path or built-in).</li>
     * </ol>
     *
     * @param inlineHtml     optional HTML string from a MongoDB {@code confs} document
     * @param pathOrResource optional file-system path (may be null)
     * @param builtinName    fallback built-in resource name, e.g. {@code "verification.html"}
     * @return the raw template content
     * @throws java.io.IOException if the file/classpath resource cannot be read
     */
    public static String loadWithFallback(String inlineHtml, String pathOrResource,
                                           String builtinName) throws java.io.IOException {
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
        try (InputStream is = EmailTemplateLoader.class
                .getClassLoader()
                .getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new IOException("Built-in email template not found on classpath: " + resourceName);
            }
            LOGGER.debug("Loading built-in email template: {}", resourceName);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
