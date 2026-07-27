package org.restheart.emails;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;

import com.softinstigate.ermes.mail.EmailService;
import com.softinstigate.ermes.mail.SMTPConfig;

/**
 * Unit tests for SmtpEmailSender's config validation, enabled/disabled
 * behavior, and per-request override signature logic.
 *
 * <p>Tests avoid exercising {@code EmailService.send()} since it opens a
 * live SMTP connection; only config parsing and wiring are covered. Any
 * SmtpEmailSender that successfully initializes a real EmailService (which
 * spawns a non-daemon thread pool) is shut down in {@link #cleanup()}.
 */
class SmtpEmailSenderTest {

    private SmtpEmailSender sender;

    @AfterEach
    void cleanup() throws Exception {
        if (sender == null) {
            return;
        }
        var field = SmtpEmailSender.class.getDeclaredField("emailSrv");
        field.setAccessible(true);
        var emailSrv = field.get(sender);
        if (emailSrv != null) {
            var shutdown = emailSrv.getClass().getMethod("shutdown", long.class);
            shutdown.invoke(emailSrv, 0L);
        }
    }

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

    private static String buildSignature(Request<?> req) throws Exception {
        Method m = SmtpEmailSender.class.getDeclaredMethod("buildSignature", Request.class);
        m.setAccessible(true);
        return (String) m.invoke(null, req);
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

    /** OverrideEntry is a private nested record; built via reflection since tests can't name it. */
    private static Object newOverrideEntry(EmailService emailSrv, String senderEmail, String appName) throws Exception {
        var overrideEntryClass = Class.forName("org.restheart.emails.SmtpEmailSender$OverrideEntry");
        var ctor = overrideEntryClass.getDeclaredConstructor(EmailService.class, String.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(emailSrv, senderEmail, appName);
    }

    private static void invokeOnOverrideEntryRemoved(SmtpEmailSender sender, Map.Entry<String, Optional<?>> entry) throws Exception {
        Method m = SmtpEmailSender.class.getDeclaredMethod("onOverrideEntryRemoved", Map.Entry.class);
        m.setAccessible(true);
        m.invoke(sender, entry);
    }

    private static boolean isShutdown(EmailService emailSrv) throws Exception {
        var field = EmailService.class.getDeclaredField("executor");
        field.setAccessible(true);
        return ((ExecutorService) field.get(emailSrv)).isShutdown();
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
        // EmailService (and its thread pool) is constructed.
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
        setConf(sender, Map.of(
                "enabled", true,
                "app-name", "Test App",
                "sender-email", "noreply@example.com",
                "smtp-hostname", "smtp.example.com",
                "smtp-port", 465,
                "smtp-username", "user",
                "smtp-password", "pass"));

        sender.onInit();

        assertTrue(sender.isEnabled());
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
    void buildSignature_joinsOverrideParamsInOrderWithEmptyPlaceholdersForMissingOnes() throws Exception {
        Request<?> req = mock(Request.class);
        when(req.attachedParam("override-emails-sender-email")).thenReturn("a@b.com");
        when(req.attachedParam("override-emails-smtp-hostname")).thenReturn("smtp.override.com");
        when(req.attachedParam("override-emails-smtp-port")).thenReturn(587);

        assertEquals("a@b.com||smtp.override.com|587||", buildSignature(req));
    }

    @Test
    void onOverrideEntryRemoved_shutsDownDistinctOverrideEmailService() throws Exception {
        sender = new SmtpEmailSender();
        var overriddenSrv = new EmailService(SMTPConfig.forSsl("smtp.override.com", 465, "user", "pass", 465), 1);
        var overrideEntry = newOverrideEntry(overriddenSrv, "from@example.com", "App");
        var cacheEntry = new AbstractMap.SimpleEntry<String, Optional<?>>("signature", Optional.of(overrideEntry));

        invokeOnOverrideEntryRemoved(sender, cacheEntry);

        assertTrue(isShutdown(overriddenSrv));
    }

    @Test
    void onOverrideEntryRemoved_neverShutsDownSharedStaticEmailService() throws Exception {
        sender = new SmtpEmailSender();
        setConf(sender, Map.of(
                "enabled", true,
                "app-name", "Test App",
                "sender-email", "noreply@example.com",
                "smtp-hostname", "smtp.example.com",
                "smtp-port", 465,
                "smtp-username", "user",
                "smtp-password", "pass"));
        sender.onInit();
        var staticSrv = getEmailSrv(sender);
        // Mirrors SmtpEmailSender#buildOverrideEntry's catch-block fallback, which reuses
        // the static emailSrv when building an override fails.
        var fallbackEntry = newOverrideEntry(staticSrv, "noreply@example.com", "Test App");
        var cacheEntry = new AbstractMap.SimpleEntry<String, Optional<?>>("signature", Optional.of(fallbackEntry));

        invokeOnOverrideEntryRemoved(sender, cacheEntry);

        assertFalse(isShutdown(staticSrv));
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
