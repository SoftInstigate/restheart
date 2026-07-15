package org.restheart.emails;

import com.softinstigate.ermes.mail.EmailModel;
import com.softinstigate.ermes.mail.EmailService;
import com.softinstigate.ermes.mail.SMTPConfig;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * RESTHeart {@link Provider} that wraps the Ermes SMTP library and exposes
 * an {@link EmailSender} implementation to other plugins via {@code @Inject("emails")}.
 *
 * <p>When {@code enabled} is {@code false} or the configuration block is absent,
 * the provider is inert: {@link #sendEmail} logs a warning and returns without
 * sending. This ensures services continue to operate even when SMTP is not
 * configured.
 *
 * <p>Expected YAML configuration block (available since 9.6.0):
 * <pre>{@code
 * emails:
 *   enabled: true
 *   app-name: "My App"
 *   sender-email: noreply@example.com
 *   smtp-hostname: email-smtp.eu-central-1.amazonaws.com
 *   smtp-port:     465
 *   smtp-username: AKIAX4NFDXSDBHZ4RU6K
 *   smtp-password: secret
 *   ssl-port:      465        # optional, defaults to 465
 * }</pre>
 *
 * @since 9.6.0
 */
@RegisterPlugin(
        name             = "emails",
        description      = "SMTP email sender (wraps ermes-mail)",
        enabledByDefault = false)
public class SmtpEmailSender implements Provider<SmtpEmailSender>, EmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpEmailSender.class);

    /** Guards both write (onInit) and read (get) of the singleton reference. */
    private static final Object LOCK = new Object();

    /**
     * Shared singleton kept after {@link #onInit()} completes so that each
     * {@link #get} call does not construct a new instance.
     */
    private static SmtpEmailSender initializedInstance;

    @Inject("config")
    private Map<String, Object> conf;

    private EmailService emailSrv;
    private String       senderEmail;
    private String       appName;
    private boolean      enabled = false;

    @OnInit
    public void onInit() {
        synchronized (LOCK) {
            this.enabled = cfgOrDefault(conf, "enabled", false);

            if (!this.enabled) {
                LOGGER.info("Emails plugin is disabled — emails will not be sent");
                initializedInstance = this;
                return;
            }

            try {
                final String smtpHostname = cfgRequired(conf, "smtpHostname");
                final int    smtpPort     = cfgRequired(conf, "smtpPort");
                final String smtpUsername = cfgRequired(conf, "smtpUsername");
                final String smtpPassword = cfgRequired(conf, "smtpPassword");
                final int    sslPort      = cfgOrDefault(conf, "sslPort", 465);

                this.emailSrv   = new EmailService(
                        SMTPConfig.forSsl(smtpHostname, smtpPort, smtpUsername, smtpPassword, sslPort),
                        4 /* thread-pool size */);
                this.appName    = cfgOrDefault(conf, "appName", "App");
                this.senderEmail = cfgRequired(conf, "senderEmail");

                initializedInstance = this;
                LOGGER.info("Emails plugin initialized — sender={}, host={}", senderEmail, smtpHostname);
            } catch (Exception e) {
                LOGGER.error("Failed to initialize Emails plugin — emails will not be sent", e);
                this.enabled = false;
                initializedInstance = this;
            }
        }
    }

    /**
     * Sends a single HTML email asynchronously via the configured SMTP relay.
     *
     * <p>Email delivery is best-effort: exceptions are caught and logged so
     * that the calling service is never blocked or failed by email errors.
     *
     * @param to            recipient email address
     * @param recipientName display name for the recipient (e.g. first name),
     *                      used in the SMTP {@code To:} header
     * @param subject       email subject line
     * @param htmlBody      full HTML body
     */
    @Override
    public void sendEmail(final String to,
                          final String recipientName,
                          final String subject,
                          final String htmlBody) {
        if (!this.enabled) {
            LOGGER.warn("Emails plugin is disabled — skipping email to <{}>", to);
            return;
        }
        try {
            var model = new EmailModel(this.senderEmail, this.appName, subject, htmlBody);
            model.addTo(to, recipientName != null ? recipientName : to);
            this.emailSrv.send(model);
            LOGGER.info("Email '{}' queued for delivery to <{}>", subject, to);
        } catch (Exception e) {
            LOGGER.error("Error sending email '{}' to <{}>", subject, to, e);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public SmtpEmailSender get(final PluginRecord<?> caller) {
        synchronized (LOCK) {
            return initializedInstance != null ? initializedInstance : this;
        }
    }

    // -------------------------------------------------------------------------
    // Configuration helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static <V> V cfgOrDefault(Map<String, Object> map, String key, V defaultValue) {
        if (map == null) return defaultValue;
        Object v = map.get(key);
        return v == null ? defaultValue : (V) v;
    }

    @SuppressWarnings("unchecked")
    private static <V> V cfgRequired(Map<String, Object> map, String key) {
        if (map == null) {
            throw new IllegalStateException("Emails plugin configuration block is missing");
        }
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalStateException("Emails plugin config: missing required key '" + key + "'");
        }
        return (V) v;
    }
}