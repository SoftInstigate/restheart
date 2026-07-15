package org.restheart.emails;

/**
 * Service Provider Interface for sending emails.
 *
 * <p>Implementations are typically registered as {@code @RegisterPlugin} providers
 * and injected into other plugins via {@code @Inject("emails")}.
 *
 * <p>This interface lives in {@code restheart-commons} so that downstream
 * modules (e.g.&nbsp;{@code restheart-cloud-server}) can depend only on
 * {@code restheart-commons} at compile time, without pulling in the concrete
 * implementation module.
 */
public interface EmailSender {

    /**
     * Sends a single HTML email.
     *
     * @param to            recipient email address
     * @param recipientName display name for the recipient (e.g. first name),
     *                      used in the SMTP {@code To:} header
     * @param subject       email subject line
     * @param htmlBody      full HTML body
     */
    void sendEmail(String to, String recipientName, String subject, String htmlBody);

    /**
     * @return {@code true} if the sender was successfully initialised and will
     *         actually deliver emails. Implementations may return {@code false}
     *         when SMTP is not configured, in which case {@link #sendEmail}
     *         should log a warning and return without sending.
     */
    boolean isEnabled();
}