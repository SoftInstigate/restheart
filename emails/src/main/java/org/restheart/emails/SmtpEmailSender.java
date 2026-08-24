package org.restheart.emails;

import com.softinstigate.ermes.mail.EmailModel;
import com.softinstigate.ermes.mail.EmailService;
import com.softinstigate.ermes.mail.SMTPConfig;
import org.restheart.exchange.Request;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.ThreadsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * RESTHeart email sender plugin. Wraps the Ermes SMTP library.
 *
 * <p>Per-request SMTP overrides are supported via attached parameters:
 * override-emails-sender-email, override-emails-sender-name,
 * override-emails-smtp-hostname, override-emails-smtp-port,
 * override-emails-smtp-username, override-emails-smtp-password.
 *
 * <p>{@code sendEmail} is synchronous: it runs the SMTP transaction on the
 * calling thread. {@code sendEmailAsync} dispatches it on the shared virtual
 * threads executor and is what callers on the request path should use, so that
 * the SMTP round trip is not added to the response latency.
 *
 * <p>{@link EmailService} is built with no internal thread pool, and holds no
 * SMTP connection of its own — every send opens and closes its own connection —
 * so overridden instances are built per request rather than cached.
 *
 * @since 9.6.0
 */
@RegisterPlugin(
        name = "emails",
        description = "SMTP email sender (wraps ermes-mail)",
        enabledByDefault = false)
public class SmtpEmailSender implements Provider<SmtpEmailSender>, EmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpEmailSender.class);
    private static final String PREFIX = "override-emails-";
    private static final Object LOCK = new Object();
    /** Ermes creates no ExecutorService with this pool size; send() runs on the calling thread. */
    private static final int NO_THREAD_POOL = 0;
    private static SmtpEmailSender initializedInstance;

    @Inject("config")
    private Map<String, Object> conf;

    private EmailService emailSrv;
    private String senderEmail;
    private String appName;
    private boolean enabled = false;

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
                this.appName = cfgOrDefault(conf, "app-name", "App");
                this.senderEmail = cfgRequired(conf, "sender-email");
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
        send(resolve(request), to, recipientName, subject, htmlBody);
    }

    /**
     * Resolves the per-request SMTP overrides on the calling thread, then runs
     * the send on the shared virtual threads executor. Reading the request up
     * front is required: by the time the task runs, the exchange may already
     * have been completed and recycled.
     */
    @Override
    public void sendEmailAsync(Request<?> request, String to, String recipientName, String subject, String htmlBody) {
        if (!this.enabled) {
            LOGGER.warn("Emails plugin disabled, skipping email to <{}>", to);
            return;
        }
        var settings = resolve(request);
        ThreadsUtils.virtualThreadsExecutor()
                .execute(() -> send(settings, to, recipientName, subject, htmlBody));
    }

    private void send(Settings settings, String to, String recipientName, String subject, String htmlBody) {
        try {
            var model = new EmailModel(settings.senderEmail(), settings.appName(), subject, htmlBody);
            model.addTo(to, recipientName != null ? recipientName : to);
            var errors = settings.emailSrv().sendSynch(model);
            if (!errors.isEmpty()) {
                LOGGER.error("Errors sending email to <{}>: {}", to, errors);
            }
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

    /** The SMTP settings a single send runs with: either the static ones or a per-request override. */
    private record Settings(EmailService emailSrv, String senderEmail, String appName) {
    }

    /**
     * Returns the settings to send with, applying the request's override
     * parameters when present and falling back to the static configuration
     * when they are absent or invalid.
     */
    private Settings resolve(Request<?> request) {
        if (request != null && hasOverride(request)) {
            var override = buildOverride(request);
            if (override != null) {
                return override;
            }
        }
        return new Settings(this.emailSrv, this.senderEmail, this.appName);
    }

    private static boolean hasOverride(Request<?> req) {
        return req.attachedParam(PREFIX + "sender-email") != null
                || req.attachedParam(PREFIX + "sender-name") != null
                || req.attachedParam(PREFIX + "smtp-hostname") != null
                || req.attachedParam(PREFIX + "smtp-port") != null
                || req.attachedParam(PREFIX + "smtp-username") != null
                || req.attachedParam(PREFIX + "smtp-password") != null;
    }

    /**
     * Builds the SMTP settings for a request carrying override parameters.
     * Starts with a full copy of the static YAML config (kebab-case keys),
     * then overrides only the fields specified via attached parameters, so
     * unspecified ones keep their static values.
     *
     * @return the overridden settings, or {@code null} if they are invalid, in
     *         which case the caller falls back to the static configuration
     */
    private Settings buildOverride(Request<?> req) {
        var m = new HashMap<String, Object>(conf != null ? conf : Map.of());
        overrideIfPresent(m, "sender-email", str(req.attachedParam(PREFIX + "sender-email")));
        overrideIfPresent(m, "app-name", str(req.attachedParam(PREFIX + "sender-name")));
        overrideIfPresent(m, "smtp-hostname", str(req.attachedParam(PREFIX + "smtp-hostname")));
        overrideIfPresent(m, "smtp-port", str(req.attachedParam(PREFIX + "smtp-port")));
        overrideIfPresent(m, "smtp-username", str(req.attachedParam(PREFIX + "smtp-username")));
        overrideIfPresent(m, "smtp-password", str(req.attachedParam(PREFIX + "smtp-password")));
        try {
            return new Settings(
                    buildEmailService(m),
                    cfgOrDefault(m, "sender-email", this.senderEmail),
                    cfgOrDefault(m, "app-name", this.appName));
        } catch (Exception e) {
            LOGGER.error("Failed to build overridden SMTP config, using the static one", e);
            return null;
        }
    }

    private static String str(Object v) {
        return v != null ? v.toString() : "";
    }

    private static void overrideIfPresent(Map<String, Object> m, String key, String value) {
        if (value != null && !value.isEmpty()) {
            if ("smtp-port".equals(key)) {
                try {
                    m.put(key, Integer.parseInt(value));
                }
                catch (NumberFormatException e) {
                    m.put(key, value);
                }
            } else {
                m.put(key, value);
            }
        }
    }

    // --- SMTP config builder ---

    private static EmailService buildEmailService(Map<String, Object> cfg) {
        final String smtpHostname = cfgRequired(cfg, "smtp-hostname");
        final int smtpPort = cfgRequired(cfg, "smtp-port");
        final String smtpUsername = cfgRequired(cfg, "smtp-username");
        final String smtpPassword = cfgRequired(cfg, "smtp-password");
        final int sslPort = cfgOrDefault(cfg, "ssl-port", 465);
        return new EmailService(
                SMTPConfig.forSsl(smtpHostname, smtpPort, smtpUsername, smtpPassword, sslPort),
                NO_THREAD_POOL);
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
