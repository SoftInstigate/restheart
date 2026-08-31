package org.restheart.emails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The built-in templates do not live here.
 *
 * <p>{@code accounts} bundles {@code verification.html}, {@code invite.html} and
 * {@code password-reset.html}; {@code stripe} bundles six of its own. This class
 * is in {@code commons} and bundles none — so resolving a built-in against
 * <em>this</em> class's classloader finds nothing the moment the caller is a
 * plugin, which is how RESTHeart loads every plugin.
 *
 * <p>That is not hypothetical. It shipped: every verification, invitation and
 * password-reset email failed to render, and because sending is best-effort the
 * caller logged a warning and returned {@code 201}. Registration looked
 * successful and no email was ever sent.
 */
class EmailTemplateLoaderTest {

    @TempDir
    Path bundle;

    private ClassLoader original;

    @BeforeEach
    void rememberContextClassloader() {
        original = Thread.currentThread().getContextClassLoader();
    }

    @AfterEach
    void restore() {
        Thread.currentThread().setContextClassLoader(original);
        EmailTemplateLoader.clearCache();
    }

    /** A classloader that carries a built-in, as a plugin jar does. */
    private ClassLoader moduleWith(String name, String content) throws Exception {
        var dir = bundle.resolve("email-templates");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), content);
        return new URLClassLoader(new URL[]{bundle.toUri().toURL()}, null);
    }

    @Test
    void aBuiltinIsFoundInTheModuleThatBundlesIt() throws Exception {
        Thread.currentThread().setContextClassLoader(
                moduleWith("verification.html", "<html>verify {{link}}</html>"));

        assertEquals("<html>verify {{link}}</html>",
                EmailTemplateLoader.load(null, "verification.html"));
    }

    @Test
    void aBuiltinNoModuleCarriesIsStillAnError() {
        // The failure has to stay loud when the resource genuinely is not there:
        // widening the search must not turn a missing template into a silent
        // empty one.
        assertThrows(IOException.class,
                () -> EmailTemplateLoader.load(null, "no-such-template.html"));
    }

    @Test
    void aFileOnDiskWinsOverTheBuiltin() throws Exception {
        Thread.currentThread().setContextClassLoader(
                moduleWith("verification.html", "built-in"));
        var custom = bundle.resolve("custom.html");
        Files.writeString(custom, "the tenant's own");

        assertEquals("the tenant's own",
                EmailTemplateLoader.load(custom.toString(), "verification.html"));
    }
}
