package org.restheart.emails;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;

import com.softinstigate.ermes.mail.EmailService;

/**
 * Unit tests for SmtpEmailSender's config validation, enabled/disabled
 * behavior, and per-request override logic.
 *
 * <p>Tests avoid exercising {@code EmailService.sendSynch()} since it opens a
 * live SMTP connection; only config parsing and wiring are covered. No cleanup
 * is needed: EmailService is built with a zero-sized thread pool, so it never
 * creates an ExecutorService and holds no resource to release.
 */
class SmtpEmailSenderTest {

    private SmtpEmailSender sender;

    private static void setConf(SmtpEmailSender sender, Map<String, Object> conf) throws Exception {
        var field = SmtpEmailSender.class.getDeclaredField("conf");
        field.setAccessible(true);
        field.set(sender, conf);
    }

    private static boolean hasOverride(Request<?> req) throws Exception {
        Method m = SmtpEmailSender.class.getDeclaredMethod("hasOverride", Request.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, req);
    }

    private static void overrideIfPresent(Map<String, Object> m, String key, String value) throws Exception {
        Method method = SmtpEmailSender.class.getDeclaredMethod("overrideIfPresent", Map.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(null, m, key, value);
    }

    private static EmailService getEmailSrv(SmtpEmailSender sender) throws Exception {
        var field = SmtpEmailSender.class.getDeclaredField("emailSrv");
        field.setAccessible(true);
        return (EmailService) field.get(sender);
    }

    /** Settings is a private nested record; invoked via reflection since tests can't name it. */
    private static Object buildOverride(SmtpEmailSender sender, Request<?> req) throws Exception {
        Method m = SmtpEmailSender.class.getDeclaredMethod("buildOverride", Request.class);
        m.setAccessible(true);
        return m.invoke(sender, req);
    }

    private static Object component(Object override, String name) throws Exception {
        var accessor = override.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(override);
    }

    private static Map<String, Object> validConf() {
        return Map.of(
                "enabled", true,
                "app-name", "Test App",
                "sender-email", "noreply@example.com",
                "smtp-hostname", "smtp.example.com",
                "smtp-port", 465,
                "smtp-username", "user",
                "smtp-password", "pass");
    }

    @Test
    void onInit_whenDisabled_pluginStaysDisabledAndSendIsNoOp() throws Exception {
        sender = new SmtpEmailSender();
        setConf(sender, Map.of("enabled", false));

        sender.onInit();

        assertFalse(sender.isEnabled());
        assertDoesNotThrow(() -> sender.sendEmail("to@example.com", "Jane", "Subject", "<p>Body</p>"));
    }

    @Test
    void onInit_whenRequiredSmtpKeyMissing_pluginEndsDisabledInsteadOfThrowing() throws Exception {
        sender = new SmtpEmailSender();
        // "smtp-hostname" intentionally missing: cfgRequired() must fail before any
        // EmailService is constructed.
        setConf(sender, Map.of(
                "enabled", true,
                "app-name", "Test App",
                "sender-email", "noreply@example.com",
                "smtp-port", 465,
                "smtp-username", "user",
                "smtp-password", "pass"));

        assertDoesNotThrow(() -> sender.onInit());

        assertFalse(sender.isEnabled());
    }

    @Test
    void onInit_whenConfigValid_pluginIsEnabled() throws Exception {
        sender = new SmtpEmailSender();
        setConf(sender, validConf());

        sender.onInit();

        assertTrue(sender.isEnabled());
        assertNotNull(getEmailSrv(sender));
    }

    @Test
    void hasOverride_falseWhenNoOverrideParamsAttached() throws Exception {
        Request<?> req = mock(Request.class);

        assertFalse(hasOverride(req));
    }

    @Test
    void hasOverride_trueWhenAtLeastOneOverrideParamAttached() throws Exception {
        Request<?> req = mock(Request.class);
        when(req.attachedParam("override-emails-smtp-hostname")).thenReturn("smtp.override.com");

        assertTrue(hasOverride(req));
    }

    @Test
    void buildOverride_appliesAttachedParamsAndKeepsStaticValuesForTheRest() throws Exception {
        sender = new SmtpEmailSender();
        setConf(sender, validConf());
        sender.onInit();

        Request<?> req = mock(Request.class);
        when(req.attachedParam("override-emails-sender-email")).thenReturn("override@example.com");
        when(req.attachedParam("override-emails-smtp-hostname")).thenReturn("smtp.override.com");
        when(req.attachedParam("override-emails-smtp-port")).thenReturn(587);

        var override = buildOverride(sender, req);

        assertNotNull(override);
        assertEquals("override@example.com", component(override, "senderEmail"));
        // "sender-name" was not overridden, so the static app-name is preserved
        assertEquals("Test App", component(override, "appName"));
        // a distinct EmailService is built for the overridden SMTP settings
        assertNotNull(component(override, "emailSrv"));
        assertTrue(component(override, "emailSrv") != getEmailSrv(sender));
    }

    @Test
    void buildOverride_returnsNullWhenResultingConfigIsInvalid() throws Exception {
        sender = new SmtpEmailSender();
        // "smtp-hostname" is missing from the static config and not supplied by the
        // override either, so building the EmailService must fail and yield null.
        setConf(sender, Map.of(
                "enabled", true,
                "app-name", "Test App",
                "sender-email", "noreply@example.com",
                "smtp-port", 465,
                "smtp-username", "user",
                "smtp-password", "pass"));

        Request<?> req = mock(Request.class);
        when(req.attachedParam("override-emails-sender-email")).thenReturn("override@example.com");

        assertNull(buildOverride(sender, req));
    }

    @Test
    void overrideIfPresent_parsesSmtpPortAsInteger() throws Exception {
        Map<String, Object> m = new HashMap<>();

        overrideIfPresent(m, "smtp-port", "587");

        assertEquals(587, m.get("smtp-port"));
    }

    @Test
    void overrideIfPresent_fallsBackToRawStringWhenSmtpPortIsNotNumeric() throws Exception {
        Map<String, Object> m = new HashMap<>();

        overrideIfPresent(m, "smtp-port", "not-a-number");

        assertEquals("not-a-number", m.get("smtp-port"));
    }

    @Test
    void overrideIfPresent_skipsNullAndEmptyValues() throws Exception {
        Map<String, Object> m = new HashMap<>();

        overrideIfPresent(m, "sender-email", null);
        overrideIfPresent(m, "sender-name", "");

        assertFalse(m.containsKey("sender-email"));
        assertFalse(m.containsKey("sender-name"));
    }

    @Test
    void overrideIfPresent_storesNonPortKeysAsString() throws Exception {
        Map<String, Object> m = new HashMap<>();

        overrideIfPresent(m, "sender-email", "override@example.com");

        assertEquals("override@example.com", m.get("sender-email"));
    }
}
