package org.restheart.emails;

import org.restheart.exchange.Request;
import org.restheart.utils.ThreadsUtils;

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
 *
 * @since 9.6.0
 */
public interface EmailSender {

    /**
     * Sends a single HTML email using the static YAML configuration.
     *
     * @param to            recipient email address
     * @param recipientName display name for the recipient (e.g. first name),
     *                      used in the SMTP {@code To:} header
     * @param subject       email subject line
     * @param htmlBody      full HTML body
     */
    void sendEmail(String to, String recipientName, String subject, String htmlBody);

    /**
     * Sends a single HTML email, reading per-request SMTP overrides from
     * attached parameters ({@code override-accounts-emails-*}).
     *
     * <p>If at least one override parameter is present, an ad-hoc SMTP
     * connection is created using the overridden values (missing ones fall
     * back to the static YAML configuration). If no override parameters are
     * present, this behaves exactly like
     * {@link #sendEmail(String, String, String, String)}.
     *
     * @param request       the current request (used to read attached override parameters)
     * @param to            recipient email address
     * @param recipientName display name for the recipient
     * @param subject       email subject line
     * @param htmlBody      full HTML body
     */
    void sendEmail(Request<?> request, String to, String recipientName, String subject, String htmlBody);

    /**
     * Dispatches {@link #sendEmail(String, String, String, String)} on the
     * shared virtual threads executor and returns immediately.
     *
     * <p>Use this on the request path, where the SMTP round trip (TCP connect,
     * TLS handshake, AUTH, DATA) would otherwise be added to the response
     * latency. Delivery is best effort: failures are logged by the
     * implementation and never surface to the caller.
     *
     * @param to            recipient email address
     * @param recipientName display name for the recipient
     * @param subject       email subject line
     * @param htmlBody      full HTML body
     */
    default void sendEmailAsync(String to, String recipientName, String subject, String htmlBody) {
        ThreadsUtils.virtualThreadsExecutor()
                .execute(() -> sendEmail(to, recipientName, subject, htmlBody));
    }

    /**
     * Dispatches {@link #sendEmail(Request, String, String, String, String)} on
     * the shared virtual threads executor and returns immediately.
     *
     * <p>Implementations that read state from {@code request} must resolve it on
     * the calling thread before dispatching: by the time the virtual thread
     * runs, the underlying exchange may already have been completed and
     * recycled. This default implementation is therefore only safe for
     * implementations that ignore {@code request}; {@code SmtpEmailSender}
     * overrides it to resolve the SMTP overrides up front.
     *
     * @param request       the current request (used to read attached override parameters)
     * @param to            recipient email address
     * @param recipientName display name for the recipient
     * @param subject       email subject line
     * @param htmlBody      full HTML body
     */
    default void sendEmailAsync(Request<?> request, String to, String recipientName, String subject, String htmlBody) {
        ThreadsUtils.virtualThreadsExecutor()
                .execute(() -> sendEmail(request, to, recipientName, subject, htmlBody));
    }

    /**
     * @return {@code true} if the sender was successfully initialised and will
     *         actually deliver emails. Implementations may return {@code false}
     *         when SMTP is not configured, in which case {@link #sendEmail}
     *         should log a warning and return without sending.
     */
    boolean isEnabled();
}
