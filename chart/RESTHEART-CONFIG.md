# RESTHeart Configuration Reference

This document describes the `restHeartConfiguration` section of `values.yaml`, which maps directly to RESTHeart's `restheart.yml` configuration file.

See also: [RESTHeart Configuration Files](https://restheart.org/docs/setup/#configuration-files)

## Listeners

| Key | Default | Description |
|-----|---------|-------------|
| `https-listener` | `false` | Enable HTTPS listener |
| `https-host` | `localhost` | HTTPS bind address |
| `https-port` | `4443` | HTTPS port |
| `http-listener` | `true` | Enable HTTP listener |
| `http-host` | `0.0.0.0` | HTTP bind address |
| `http-port` | `8080` | HTTP port |
| `ajp-listener` | `false` | Enable AJP listener |
| `ajp-host` | `localhost` | AJP bind address |
| `ajp-port` | `8009` | AJP port |

When HTTPS is enabled, set `containerPort: 4443` in `values.yaml` to match.

## TLS / Keystore

| Key | Description |
|-----|-------------|
| `keystore-file` | Path to Java keystore file |
| `keystore-password` | Keystore password |
| `certpassword` | Certificate password |

See [TLS Configuration](https://restheart.org/docs/security/tls/).

## Instance

| Key | Default | Description |
|-----|---------|-------------|
| `instance-name` | `default` | Instance name (logged, used for custom code) |
| `instance-base-url` | `""` | Base URL when behind reverse proxy |

## MongoDB

| Key | Default | Description |
|-----|---------|-------------|
| `mongo-uri` | `""` | [MongoDB connection string](https://docs.mongodb.com/manual/reference/connection-string/) |
| `mongo-mounts` | `[{what: /restheart, where: /}]` | Map MongoDB resources to API URIs |
| `default-representation-format` | `STANDARD` | Document representation format |

### Mount examples

```yaml
# Expose all databases
mongo-mounts:
  - what: "*"
    where: /

# Bind specific database
mongo-mounts:
  - what: /mydb
    where: /api
```

## Security

### Authentication Mechanisms

| Mechanism | Default | Notes |
|-----------|---------|-------|
| `tokenBasicAuthMechanism` | enabled | Token + Basic auth |
| `basicAuthMechanism` | enabled | Uses `mongoRealmAuthenticator` |
| `jwtAuthenticationMechanism` | disabled | Configure `algorithm`, `key`, `issuer` |
| `digestAuthMechanism` | disabled | Requires plaintext passwords |
| `identityAuthMechanism` | disabled | Fixed identity for testing |

### Authenticators

| Authenticator | Default | Description |
|---------------|---------|-------------|
| `mongoRealmAuthenticator` | enabled | Users in MongoDB `restheart.users` |
| `fileRealmAuthenticator` | disabled | Users from YAML file |

**`mongoRealmAuthenticator` key settings:**

| Key | Default | Description |
|-----|---------|-------------|
| `users-db` | `restheart` | Database name |
| `users-collection` | `users` | Collection name |
| `bcrypt-hashed-password` | `true` | Passwords are bcrypt hashed |
| `create-user` | `true` | Auto-create user on startup |
| `create-user-document` | `""` | JSON document for initial admin user |

To set a custom admin password:

```yaml
restHeartConfiguration:
  authenticators:
    mongoRealmAuthenticator:
      create-user-document: '{"_id": "admin", "password": "$2a$12$HASH_HERE", "roles": ["admin"]}'
```

Use `bcrypt-generator.com` and replace `$2y` with `$2a`.

### Authorizers

| Authorizer | Default | Description |
|------------|---------|-------------|
| `mongoAclAuthorizer` | enabled | ACL in MongoDB `restheart.acl` |
| `fileAclAuthorizer` | disabled | ACL from YAML file |
| `originVetoer` | disabled | CSRF protection via Origin whitelist |
| `fullAuthorizer` | disabled | Allow all authenticated requests |

### Token Manager

| Manager | Default | Description |
|---------|---------|-------------|
| `rndTokenManager` | enabled | Random token, 15min TTL |
| `jwtTokenManager` | disabled | JWT-based tokens |

## Plugins

| Key | Default | Description |
|-----|---------|-------------|
| `plugins-directory` | `plugins` | Directory for plugin JARs |

## Services

| Service | URI | Description |
|---------|-----|-------------|
| `mongo.uri` | `/` | MongoDB REST API |
| `authTokenService.uri` | `/tokens` | Token management |
| `ping.uri` | `/ping` | Health check |
| `roles.uri` | `/roles` | Roles endpoint |
| `graphql.uri` | `/graphql` | GraphQL endpoint |

## Logging

| Key | Default | Description |
|-----|---------|-------------|
| `enable-log-console` | `true` | Log to console |
| `enable-log-file` | `false` | Log to file |
| `log-file-path` | `restheart.log` | Log file path |
| `log-level` | `INFO` | OFF, ERROR, WARN, INFO, DEBUG, TRACE, ALL |
| `requests-log-level` | `1` | 0=none, 1=light, 2=detailed (dev only) |
| `metrics-gathering-level` | `DATABASE` | OFF, ROOT, DATABASE, COLLECTION |

## Performance

| Key | Default | Description |
|-----|---------|-------------|
| `io-threads` | `0` | I/O threads (0 = number of cores) |
| `buffer-size` | `16364` | Buffer size in bytes |
| `direct-buffers` | `true` | Use native I/O buffers |
