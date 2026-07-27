package org.restheart.emails;

import com.softinstigate.ermes.mail.EmailModel;
import com.softinstigate.ermes.mail.EmailService;
import com.softinstigate.ermes.mail.SMTPConfig;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.exchange.Request;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * RESTHeart email sender plugin. Wraps the Ermes SMTP library.
 *
 * <p>Per-request SMTP overrides are supported via attached parameters:
 * override-emails-sender-email, override-emails-sender-name,
 * override-emails-smtp-hostname, override-emails-smtp-port,
 * override-emails-smtp-username, override-emails-smtp-password.
 *
 * <p>Overridden EmailService instances are cached to avoid re-instantiation.
 *
 * @since 9.6.0
 */
@RegisterPlugin(
        name             = "emails",
        description      = "SMTP email sender (wraps ermes-mail)",
        enabledByDefault = false)
public class SmtpEmailSender implements Provider<SmtpEmailSender>, EmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpEmailSender.class);
    private static final String PREFIX = "override-emails-";
    private static final Object LOCK = new Object();
    private static final long OVERRIDE_ENTRY_SHUTDOWN_TIMEOUT_SECONDS = 5;
    private static SmtpEmailSender initializedInstance;

    @Inject("config")
    private Map<String, Object> conf;

    private EmailService emailSrv;
    private String       senderEmail;
    private String       appName;
    private boolean      enabled = false;

    /** Cache for overridden EmailService instances, keyed by override signature. */
    private LoadingCache<String, OverrideEntry> overrideCache;

    @OnInit
    public void onInit() {
        synchronized (LOCK) {
            this.enabled = cfgOrDefault(conf, "enabled", false);
            if (!this.enabled) {
                LOGGER.info("Emails plugin is disabled");
                initializedInstance = this;
                return;
            }
            try {
                this.emailSrv = buildEmailService(conf);
                this.appName    = cfgOrDefault(conf, "app-name", "App");
                this.senderEmail = cfgRequired(conf, "sender-email");
                this.overrideCache = CacheFactory.createLocalLoadingCache(
                        64, Cache.EXPIRE_POLICY.AFTER_READ, 600_000, this::buildOverrideEntry, this::onOverrideEntryRemoved);
                initializedInstance = this;
                LOGGER.info("Emails plugin initialized sender={} host={}", senderEmail,
                        cfgOrDefault(conf, "smtp-hostname", "?"));
            } catch (Exception e) {
                LOGGER.error("Failed to initialize Emails plugin", e);
                this.enabled = false;
                initializedInstance = this;
            }
        }
    }

    @Override
    public void sendEmail(String to, String recipientName, String subject, String htmlBody) {
        sendEmail(null, to, recipientName, subject, htmlBody);
    }

    @Override
    public void sendEmail(Request<?> request, String to, String recipientName, String subject, String htmlBody) {
        if (!this.enabled) {
            LOGGER.warn("Emails plugin disabled, skipping email to <{}>", to);
            return;
        }

        final EmailService srv;
        final String from;
        final String name;

        if (request != null && hasOverride(request)) {
            var signature = buildSignature(request);
            var entry = overrideCache
                    .getLoading(signature)
                    .orElse(new OverrideEntry(this.emailSrv, this.senderEmail, this.appName));
            srv  = entry.emailSrv;
            from = entry.senderEmail;
            name = entry.appName;
        } else {
            srv  = this.emailSrv;
            from = this.senderEmail;
            name = this.appName;
        }

        try {
            var model = new EmailModel(from, name, subject, htmlBody);
            model.addTo(to, recipientName != null ? recipientName : to);
            srv.send(model);
        } catch (Exception e) {
            LOGGER.error("Error sending email to <{}>", to, e);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public SmtpEmailSender get(PluginRecord<?> caller) {
        synchronized (LOCK) {
            return initializedInstance != null ? initializedInstance : this;
        }
    }

    // --- Override support ---

    private record OverrideEntry(EmailService emailSrv, String senderEmail, String appName) {}

    private static boolean hasOverride(Request<?> req) {
        return req.attachedParam(PREFIX + "sender-email") != null
            || req.attachedParam(PREFIX + "sender-name") != null
            || req.attachedParam(PREFIX + "smtp-hostname") != null
            || req.attachedParam(PREFIX + "smtp-port") != null
            || req.attachedParam(PREFIX + "smtp-username") != null
            || req.attachedParam(PREFIX + "smtp-password") != null;
    }

    private static String buildSignature(Request<?> req) {
        return String.join("|",
                str(req.attachedParam(PREFIX + "sender-email")),
                str(req.attachedParam(PREFIX + "sender-name")),
                str(req.attachedParam(PREFIX + "smtp-hostname")),
                str(req.attachedParam(PREFIX + "smtp-port")),
                str(req.attachedParam(PREFIX + "smtp-username")),
                str(req.attachedParam(PREFIX + "smtp-password")));
    }

    private static String str(Object v) {
        return v != null ? v.toString() : "";
    }

    /**
     * Builds an OverrideEntry from the cache key (pipe-separated signature).
     * Starts with a full copy of the static YAML config (kebab-case keys),
     * then overrides only the fields specified via attached parameters.
     * Empty parts in the signature are skipped, so static config values are preserved.
     */
    @SuppressWarnings("unchecked")
    private OverrideEntry buildOverrideEntry(String signature) {
        var parts = signature.split("\\|", -1);
        var m = new java.util.HashMap<String, Object>(conf != null ? conf : Map.of());
        overrideIfPresent(m, "sender-email",   parts[0]);
        overrideIfPresent(m, "app-name",       parts[1]);
        overrideIfPresent(m, "smtp-hostname",  parts[2]);
        overrideIfPresent(m, "smtp-port",      parts[3]);
        overrideIfPresent(m, "smtp-username",  parts[4]);
        overrideIfPresent(m, "smtp-password",  parts[5]);
        try {
            var srv  = buildEmailService(m);
            var from = cfgOrDefault(m, "sender-email", this.senderEmail);
            var name = cfgOrDefault(m, "app-name", this.appName);
            return new OverrideEntry(srv, from, name);
        } catch (Exception e) {
            LOGGER.error("Failed to build overridden SMTP config", e);
            return new OverrideEntry(this.emailSrv, this.senderEmail, this.appName);
        }
    }

    /**
     * Shuts down the overridden EmailService's thread pool when its cache entry is
     * removed (expiration, eviction, or explicit invalidation). Never shuts down the
     * static {@link #emailSrv}, which {@link #buildOverrideEntry} falls back to when
     * building an override fails, and which must stay alive for the plugin's lifetime.
     */
    private void onOverrideEntryRemoved(Map.Entry<String, Optional<OverrideEntry>> entry) {
        entry.getValue().ifPresent(overrideEntry -> {
            if (overrideEntry.emailSrv() != this.emailSrv) {
                overrideEntry.emailSrv().shutdown(OVERRIDE_ENTRY_SHUTDOWN_TIMEOUT_SECONDS);
            }
        });
    }

    private static void overrideIfPresent(Map<String, Object> m, String key, String value) {
        if (value != null && !value.isEmpty()) {
            if ("smtp-port".equals(key) || "ssl-port".equals(key)) {
                try { m.put(key, Integer.parseInt(value)); }
                catch (NumberFormatException e) { m.put(key, value); }
            } else {
                m.put(key, value);
            }
        }
    }

    // --- SMTP config builder ---

    private static EmailService buildEmailService(Map<String, Object> cfg) {
        final String smtpHostname = cfgRequired(cfg, "smtp-hostname");
        final int    smtpPort     = cfgRequired(cfg, "smtp-port");
        final String smtpUsername = cfgRequired(cfg, "smtp-username");
        final String smtpPassword = cfgRequired(cfg, "smtp-password");
        final int    sslPort      = cfgOrDefault(cfg, "ssl-port", 465);
        return new EmailService(
                SMTPConfig.forSsl(smtpHostname, smtpPort, smtpUsername, smtpPassword, sslPort),
                4);
    }

    // --- Configuration helpers ---

    @SuppressWarnings("unchecked")
    private static <V> V cfgOrDefault(Map<String, Object> map, String key, V defaultValue) {
        if (map == null) return defaultValue;
        Object v = map.get(key);
        return v == null ? defaultValue : (V) v;
    }

    @SuppressWarnings("unchecked")
    private static <V> V cfgRequired(Map<String, Object> map, String key) {
        if (map == null) throw new IllegalStateException("Emails plugin configuration block is missing");
        Object v = map.get(key);
        if (v == null) throw new IllegalStateException("Emails plugin config: missing required key '" + key + "'");
        return (V) v;
    }
}