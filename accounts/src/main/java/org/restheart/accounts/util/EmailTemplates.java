package org.restheart.accounts.util;

public final class EmailTemplates {

    private EmailTemplates() {}

    public static String verifyEmailSubject(String appName) {
        return appName + " — Verify your email address";
    }

    public static String verifyEmailBody(String firstName, String verificationLink, String appName) {
        return wrap(appName, "Verify your email address",
            "<p style=\"font-size:16px;color:#1a202c;\">Hi " + esc(firstName) + ",</p>" +
            "<p style=\"color:#4a5568;\">Thanks for signing up for <strong>" + esc(appName) + "</strong>! " +
            "Please confirm your email address by clicking the button below. " +
            "The link expires in 24 hours.</p>" +
            actionButton("Verify my email", verificationLink) +
            "<p style=\"color:#718096;font-size:14px;\">If the button doesn't work, copy and " +
            "paste this link into your browser:</p>" +
            "<p style=\"font-size:13px;word-break:break-all;\"><a href=\"" + verificationLink +
            "\" style=\"color:#6366f1;\">" + esc(verificationLink) + "</a></p>" +
            "<p style=\"color:#718096;font-size:14px;\">If you didn't create an account, " +
            "you can safely ignore this email.</p>");
    }

    public static String resetPasswordSubject(String appName) {
        return appName + " — Reset your password";
    }

    public static String resetPasswordBody(String firstName, String resetLink, String appName) {
        return wrap(appName, "Reset your password",
            "<p style=\"font-size:16px;color:#1a202c;\">Hi " + esc(firstName) + ",</p>" +
            "<p style=\"color:#4a5568;\">We received a request to reset the password for your " +
            "<strong>" + esc(appName) + "</strong> account. Click the button below to choose a new password. " +
            "This link expires in 1 hour.</p>" +
            actionButton("Reset my password", resetLink) +
            "<p style=\"color:#718096;font-size:14px;\">If the button doesn't work, copy and " +
            "paste this link into your browser:</p>" +
            "<p style=\"font-size:13px;word-break:break-all;\"><a href=\"" + resetLink +
            "\" style=\"color:#6366f1;\">" + esc(resetLink) + "</a></p>" +
            "<p style=\"color:#718096;font-size:14px;\">If you didn't request a password reset, " +
            "please ignore this email — your password will not change.</p>");
    }

    public static String inviteSubject(String teamName, String appName) {
        return "You've been invited to join " + teamName + " on " + appName;
    }

    public static String inviteBody(String teamName, String inviterName, String activationLink, String appName) {
        return wrap(appName, "You've been invited to " + esc(teamName),
            "<p style=\"font-size:16px;color:#1a202c;\">Hi there,</p>" +
            "<p style=\"color:#4a5568;\"><strong>" + esc(inviterName) + "</strong> has invited " +
            "you to join the team <strong>" + esc(teamName) + "</strong> on <strong>" + esc(appName) + "</strong>.</p>" +
            "<p style=\"color:#4a5568;\">Click the button below to accept the invitation and " +
            "set up your account. The link expires in 7 days.</p>" +
            actionButton("Accept invitation", activationLink) +
            "<p style=\"color:#718096;font-size:14px;\">If the button doesn't work, copy and " +
            "paste this link into your browser:</p>" +
            "<p style=\"font-size:13px;word-break:break-all;\"><a href=\"" + activationLink +
            "\" style=\"color:#6366f1;\">" + esc(activationLink) + "</a></p>" +
            "<p style=\"color:#718096;font-size:14px;\">If you were not expecting this invitation, " +
            "you can safely ignore this email.</p>");
    }

    private static String wrap(String appName, String title, String content) {
        var footer = "<p style=\"font-size:12px;color:#888;margin-top:32px;border-top:1px solid #e2e8f0;" +
                     "padding-top:16px;\">&copy; " + esc(appName) + "</p>";
        return "<!DOCTYPE html><html lang=\"en\"><head>" +
            "<meta charset=\"UTF-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>" + esc(title) + "</title></head>" +
            "<body style=\"margin:0;padding:0;background:#f7f8fa;font-family:Arial,sans-serif;\">" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">" +
            "<tr><td align=\"center\" style=\"padding:40px 16px;\">" +
            "<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" " +
            "style=\"max-width:600px;background:#ffffff;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,.08);\">" +
            "<tr><td style=\"background:#6366f1;border-radius:8px 8px 0 0;padding:28px 40px;\">" +
            "<span style=\"font-size:24px;font-weight:700;color:#ffffff;letter-spacing:-0.5px;\">" + esc(appName) + "</span>" +
            "</td></tr>" +
            "<tr><td style=\"padding:40px;\">" + content + footer + "</td></tr>" +
            "</table></td></tr></table></body></html>";
    }

    private static String actionButton(String label, String href) {
        return "<table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:28px 0;\">" +
            "<tr><td style=\"background:#6366f1;border-radius:6px;\">" +
            "<a href=\"" + href + "\" style=\"display:inline-block;padding:14px 28px;color:#ffffff;" +
            "font-weight:600;font-size:15px;text-decoration:none;border-radius:6px;\">" +
            esc(label) + "</a></td></tr></table>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
