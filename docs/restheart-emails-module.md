# restheart-emails Module — Implementation Spec

## Goal

Extract the `Ermes` email-sending plugin from `restheart-accounts` into a standalone reusable module `restheart-emails`. Rename the plugin from `ermes` to `emails` while keeping the same YAML config keys. Move the `EmailSender` SPI from `org.restheart.plugins.accounts` to `org.restheart.emails`.

**Version:** 9.6.0+

---

## Naming Convention

| Item | Value |
|------|-------|
| Maven module | `restheart-emails` (directory `restheart/emails/`) |
| Maven artifactId | `restheart-emails` |
| Java package | `org.restheart.emails` |
| Plugin name (YAML key) | `emails` |
| Inject name | `emails` |
| Implementation class | `SmtpEmailSender` |
| SPI interface | `EmailSender` (in `org.restheart.emails`, lives in `restheart-commons`) |

---

## Architecture

```
restheart-commons  ->  org.restheart.emails.EmailSender  (SPI interface)
restheart-emails   ->  org.restheart.emails.SmtpEmailSender  (implementation, depends on ermes-mail)
restheart-accounts ->  @Inject("emails") EmailSender emails  (consumer, no ermes-mail dep)
restheart-cloud-server -> @Inject("emails") EmailSender emails  (consumer)
```

---

## YAML Configuration

### Before (ermes:)
```yaml
ermes:
  enabled: true
  app-name: "My App"
  sender-email: noreply@example.com
  smtp-hostname: email-smtp.eu-central-1.amazonaws.com
  smtp-port: 465
  smtp-username: AKIAX4NFDXSDBHZ4RU6K
  smtp-password: secret
```

### After (emails:)
```yaml
emails:
  enabled: true
  app-name: "My App"
  sender-email: noreply@example.com
  smtp-hostname: email-smtp.eu-central-1.amazonaws.com
  smtp-port: 465
  smtp-username: AKIAX4NFDXSDBHZ4RU6K
  smtp-password: secret
```

Config keys are identical — only the top-level YAML key changes from `ermes` to `emails`.

---

## Status

### DONE — restheart/ monorepo

- [x] commons/src/main/java/org/restheart/emails/EmailSender.java — SPI interface
- [x] commons/src/main/java/org/restheart/plugins/accounts/EmailSender.java — DELETED
- [x] emails/pom.xml — new Maven module (AGPL + commercial dual license)
- [x] emails/src/main/java/org/restheart/emails/SmtpEmailSender.java — @RegisterPlugin(name = "emails")
- [x] pom.xml — module emails added after accounts
- [x] accounts/pom.xml — ermes-mail dep removed, restheart-emails dep added (provided scope)
- [x] accounts/email/Ermes.java — DELETED
- [x] accounts/ForgotPasswordService.java — Ermes -> EmailSender, @Inject("ermes") -> @Inject("emails"), Errors import restored
- [x] accounts/InviteService.java — same + ermes.sendEmail -> emails.sendEmail, duplicate import removed
- [x] accounts/RegisterService.java — same
- [x] accounts/ResendInviteService.java — same + log message updated

### DONE — Verify restheart/ build

Build and verify passed successfully.

    cd /Users/uji/development/restheart && ./mvnw clean compile -DskipUpdateLicense=true

### PENDING — restheart-cloud-server/ Java files

All @Inject("ermes") already changed to @Inject("emails"). Remaining field renames and usages:

- [ ] NotificationService.java — this.ermes -> this.emails in sendNotification() and sendNotificationWithRetry()
- [ ] PluginManagementService.java — ermes -> emails in NotificationService(ermes, env) and WebhookEmailSender(ermes, ...) calls
- [ ] ProvisionFree.java — ermes -> emails in NotificationService(ermes, env) call
- [ ] WebhookInterceptor.java — ermes -> emails in WebhookEmailSender(ermes, ...) call
- [ ] TipEmailScheduler.java — field ermes -> emails (verify)
- [ ] TipEmailSender.java — field ermes -> emails (verify)
- [ ] SignupNotificationInterceptor.java — field ermes -> emails, ermes.sendEmail -> emails.sendEmail (verify)
- [ ] IdleServiceChecker.java — field ermes -> emails (verify)
- [ ] IdleServiceDeactivator.java — field ermes -> emails (verify)
- [ ] WebhookHandler.java — field Ermes ermes -> EmailSender emails, NotificationService(ermes, env) -> NotificationService(emails, env) (verify)
- [ ] TeamConfigInterceptor.java — accounts.containsKey("ermes") -> accounts.containsKey("emails"), override-accounts-ermes-* -> override-accounts-emails-* (done, verify)

### PENDING — restheart-cloud-server/etc/*.yml config files

Two config patterns exist across 11+ files:

Pattern 1 — RHO style (single-line overrides):
    /ermes: -> /emails:
    /ermes/enabled: -> /emails/enabled:
    /ermes/baseAppUrl: -> /emails/baseAppUrl:
    /ermes/appName: -> /emails/appName:
    /ermes/senderEmail: -> /emails/senderEmail:
    ...etc

Pattern 2 — YAML expanded (indented block):
    ermes: -> emails:
      enabled: true
      app-name: "My App"
      ...

Files to update:
- [ ] it-admin.yml (both patterns)
- [ ] it-dedicated.yml (pattern 2)
- [ ] it-free.yml (both patterns)
- [ ] it-shared.yml (pattern 2)
- [ ] localhost.yml (pattern 1)
- [ ] prod-admin.yml (pattern 1)
- [ ] prod-free.yml (both patterns)
- [ ] prod-shared.yml (both patterns)
- [ ] srv-dedicated.yml (pattern 1)
- [ ] test-admin.yml (pattern 1)
- [ ] test-free.yml (both patterns)
- [ ] test-shared.yml (both patterns)

Also update comments mentioning "Ermes" -> "Emails (email-sender module)".

### PENDING — restheart-website/ documentation

- [ ] Create docs/plugins/emails.md — plugin docs (config reference, @Inject("emails") usage, version 9.6.0+)
- [ ] Update _includes/docs-sidebar.html — add link under security section

### PENDING — Final verification

    cd /Users/uji/development/restheart && ./mvnw clean compile -DskipUpdateLicense=true
    cd /Users/uji/development/restheart-cloud/restheart-cloud-server && ./mvnw clean compile -DskipUpdateLicense=true
