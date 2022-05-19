# User Signup

This example implements a user signup process.

To create an user, an unauthenticated client executes a POST request to `/users`; this creates a new user document.

When the new document gets created:

- the request interceptor `verificationCodeGenerator` adds the verification code to the user document and sets `roles: ["UNVERIFIED"]` (the ACL defines no permissions for this role)
- the async response interceptor `emailVerificationSender` sends the verification email with the link to the `userVerifier` service with the verification code passed via a query parameter
- the service `userVerifier` checks the verification code and unlocks (ie. set the roles=["USER"]) the user


### Deploy

1. `cd user-signup`
1. Build the plugin with `../mvnw package` (uses the maven-dependency-plugin to copy the jar of the external dependency to /target/lib)
1. Copy both the plugins JAR `user-signup/target/user-signup.jar` and `user-signup/target/lib/*` into the directory `<RH_HOME>/plugins`

### Configuration

You need to configure the `emailVerificationSender` interceptor with the connection parameters of an SMTP server (needed to send the verification emails). Add the following snippet to the `plugins-args` section of `restheart.yml` (this has been tested with Gmail but any SMTP server with SSL support should work):

```yml
plugins-args:
  emailVerificationSender:
    verifier-srv-url: http://127.0.0.1:8080/verify
    from: <your-email-address>
    from-name: <your-name>
    host: smtp.gmail.com
    port: 465
    smtp-username: <your-gmail-address>
    smtp-password: <your-gmail-password>
```

> For Gmail you need to create a third party password  as described [here](https://support.google.com/accounts/answer/185833?hl=en)

## Validation

[JSON Schema Validation](https://restheart.org/docs/json-schema-validation/) is used to enforce the schema to the user document.

> The following request use [httpie](https://httpie.org) http cli client.

#### Create the schema store

```bash
$ http -a admin:secret PUT :8080/_schemas
```

#### Create the JSON Schema for user

```bash
$ echo '{"_id":"user","$schema":"http://json-schema.org/draft-04/schema#","type":"object","properties":{"_id":{"type":"string","pattern":"^\\\w+@[a-zA-Z_]+?.[a-zA-Z]{2,3}$"},"password":{"type":"string"},"roles":{"type":"array","items":{"type":"string"}},"code":{"type":"string"}},"required":["_id","password"],"additionalProperties":false}' | http -a admin:secret POST :8080/_schemas
```

#### Update the metadata of /users to apply the schema validation

```bash
$ echo '{"jsonSchema":{"schemaId":"user"}}' | http -a admin:secret PUT :8080/users
```

#### Create test collection and few documents

```bash
$ http -a admin:secret :8080/coll
$ echo '[{"n":1},{"n":2},{"n":3}]' | http -a admin:secret :8080/coll
```

## ACL

To allow unauthenticated clients to create user documents add the following permission to `/acl`:

```bash
$ echo '{"_id":"allowToRegister","predicate":"path[/users] and method[POST]", "roles":["$unauthenticated"],"priority":1}' | http -a admin:secret POST :8080/acl
```

Give the role `USER` some permissions on the collection `/coll`

```bash
$ echo '{"_id":"5f0f0e1785a5d15404f953bb","predicate":"path[/coll] and (method[POST] or method[GET])","roles":["USER"],"priority":1}' | http -a admin:secret POST :8080/acl
```

## Create user and verify the email address

The following unauthenticated request creates the user document with your email address:

```bash
$ http :8080/users _id=<your-email-address> password=<your-password>
```

The new user will automatically get the role `UNVERIFIED` and the verification email will be sent. Clicking on the link in the verification email unlocks the user giving her the role 'USER'.

For a complete guide on how to check the user credentials read [Suggested way to check credentials](https://restheart.org/docs/security/how-clients-authenticate/#suggested-way-to-check-credentials).

In short, the `/roles` service can be used to check the user credentials. If the user has not yet verified the email, calling the roles service with the correct credentials returns the role `UNVERIFIED` and the UX should display an error message saying that the email address has not been verified.

```
$ http -a <your-email-address>:<your-password> :8080/roles/<your-email-address>

{
    "authenticated": true,
    "roles": [
        "UNVERIFIED"
    ]
}
```

Let try to GET the collection `/coll` with the new user. The request fails with `403 Forbidden` because the user role is `UNVERIFIED` and no permissions are given to it.

```bash
http -a <your-email-address>:<your-password> GET :8080/coll

HTTP/1.1 403 Forbidden
```

Check the email and verify your address by clicking in the verification link.

Now the roles service will return the role `USER`.

```bash
$ http -a <your-email-address>:<your-password> :8080/roles/<your-email-address>

{
    "authenticated": true,
    "roles": [
        "USER"
    ]
}
```

Let try to GET the collection `/coll` with the verified user. The request succeeds with `200 Created` because the user has now role `USER` and it has a permission to execute the request.

```bash
$ http -a <your-email-address>:<your-password> :8080/coll

[
    {
        "_etag": { "$oid": "625ebed9ba3f47380ebe6953" },
        "_id": { "$oid": "625ebed9ba3f47380ebe6956" },
        "n": 3
    },
    {
        "_etag": { "$oid": "625ebed9ba3f47380ebe6953" },
        "_id": { "$oid": "625ebed9ba3f47380ebe6955" },
        "n": 2
    },
    {
        "_etag": { "$oid": "625ebed9ba3f47380ebe6953" },
        "_id": { "$oid": "625ebed9ba3f47380ebe6954" },
        "n": 1
    }
]
```
