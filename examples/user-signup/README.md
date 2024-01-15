# User Signup Process Implementation

## Overview

This example demonstrates an implementation of a user signup process, including user document creation, verification code generation, and email verification.

## Process Flow

- **User Creation**: An unauthenticated client sends a POST request to `/users` to create a new user document.
- **Verification Code**: The `verificationCodeGenerator` interceptor adds a verification code to the user document upon creation.
- **User Roles**: The newly created user is assigned `roles: ["UNVERIFIED"]`. This is set by the `mergeRequest` statement in the permission, allowing unauthenticated clients to create user documents. The role `UNVERIFIED` is defined in the ACL with no permissions.
- **Email Verification**: The `emailVerificationSender` async response interceptor sends a verification email. This email contains a link to the `userVerifier` service, passing the verification code as a query parameter.
- **User Verification**: The `userVerifier` service plays a crucial role in the verification process. Upon receiving the verification code, it authenticates the code's validity. Once verified, the service updates the user's role to `["USER"]`. This updated role is significant as it is predefined in the Access Control List (ACL) with specific permissions. Notably, it grants the verified user the ability to access the collection `/coll`. This access is essential as it enables the verified user to interact with the core data of the dummy application, marking a successful transition from an unverified to a fully authenticated and functional user within the system.

## Building the Plugin

Use the following command to build the plugin. Ensure you are in the project's root directory before executing it:

```bash
$ ../mvnw clean package
```

## Running RESTHeart with the plugin

To run the RESTHeart with the plugin, use Docker as follows. This command maps the host's port 8080 to the container's port 8080 and mounts the build directory as a volume:

1) **Start MongoDB Container**

**For First-Time Setup:**

Use the following commands to start a MongoDB container and initialize it as a single node replica set.

```bash
$ docker run -d --name mongodb -p 27017:27017 mongo --replSet=rs0 # Launch a MongoDB container
$ docker exec mongodb mongosh --quiet --eval "rs.initiate()" # Initialize the MongoDB instance to work as a single node replica set
```

**For Subsequent Uses:**

If you've previously created the MongoDB container and just need to start it again, you can simply use the following command:

```bash
$ docker run mongodb
```

2) **Launch RESTHeart Container**

Run the RESTHeart container, linking it to the MongoDB container and using the configuration override file `conf.yml`:

```bash
$ docker run --name restheart --rm -p "8080:8080" -v ./target:/opt/restheart/plugins/custom -v ./conf.yml:/opt/restheart/etc/conf.yml softinstigate/restheart:latest -o etc/conf.yml
```

For more information see: [For development: run RESTHeart and open MongoDB with Docker](https://restheart.org/docs/setup-with-docker#for-development-run-restheart-and-open-mongodb-with-docker)

**Important Configuration Step**: Before executing this script, ensure you have customized the `conf.yml` file with your SMTP server details. This configuration is crucial for the script to facilitate the sending of verification emails.

For detailed instructions on configuring your SMTP server in `conf.yml`, refer to the upcoming section titled **Configuration**.

## Configuration

Configure the `emailVerificationSender` interceptor with your SMTP server details for sending verification emails. Add the following in `restheart.yml`:

```yml
emailVerificationSender:
verifier-srv-url: http://127.0.0.1:8080/verify
from: <your-email-address>
from-name: <your-name>
host: smtp.gmail.com
port: 465
smtp-username: <your-gmail-address>
smtp-password: <your-gmail-password>
```

> Note: For Gmail, create a third-party password as described [here](https://support.google.com/accounts/answer/185833?hl=en).

## Schema Validation

[JSON Schema Validation](https://restheart.org/docs/json-schema-validation/) is used to ensure the user document adheres to a defined schema.

### Steps to Apply Schema Validation
1. **Create Schema Store**:
   ```bash
   $ http -a admin:secret PUT :8080/_schemas
   ```
2. **Define JSON Schema for User**:
   ```bash
   $ echo '{"_id":"user","$schema":"http://json-schema.org/draft-04/schema#","type":"object","properties":{"_id":{"type":"string","pattern":"^\\w+@[a-zA-Z_]+?.[a-zA-Z]{2,3}$"},"password":{"type":"string"},"roles":{"type":"array","items":{"type":"string"}},"code":{"type":"string"}},"required":["_id","password"],"additionalProperties":false}' | http -a admin:secret POST :8080/_schemas
   ```
3. **Apply Schema to Users**:
   ```bash
   $ echo '{"jsonSchema":{"schemaId":"user"}}' | http -a admin:secret PUT :8080/users
   ```

## Access Control List (ACL)

- **Allow Unauthenticated User Creation**: Add permission to `/acl` for unauthenticated clients to create user documents.

NOTE: The permission sets `roles: ["UNVERIFIED"]` using the `mergeRequest` statement.

  ```bash
  $ echo '{"_id":"allowToRegister","predicate":"path[/users] and method[POST]", "roles":["$unauthenticated"], "mongo": {"mergeRequest":{"roles":["UNVERIFIED"]}}, "priority":1}' | http -a admin:secret POST :8080/acl
  ```
- **Assign Permissions to `USER` Role**:

NOTE: The permission restricts user access to own documents using `mergeRequest`, `readFilter` and  `writeFilter` statements.

  ```bash
  $ echo '{"_id":"allowUsersToAccessColl","predicate":"path[/coll] and (method[POST] or method[GET])","roles":["USER"],  "mongo":  "mergeRequest": {"author": "@user._id"},"readFilter":{"author": "@user._id"},"writeFilter":{"author": "@user._id"} }, "priority":1}' | http -a admin:secret POST :8080/acl
  ```

## User Creation and Verification
- **Create User**: Unauthenticated request to create a user document.
  ```bash
  $ http POST :8080/users _id=<your-email-address> password=<your-password>
  ```
- **Verify Email Address**: Click the link in the verification email. This changes the user's role to `USER`.
- **Check User Roles**: Use the `/roles` service to verify user credentials and roles.
  ```bash
  $ http :8080/roles/<your-email-address> -a <your-email-address>:<your-password>
  ```

## Testing Permissions

- **Access Collection as `UNVERIFIED` User**: Expect `403 Forbidden`.
  ```bash
  http GET :8080/coll -a <your-email-address>:<your-password>
  ```
- **Access Collection as `USER`**: Post-verification, expect successful access.
  ```bash
  http GET :8080/coll -a <your-email-address>:<your-password>
  ```

For a complete guide on credential checking, refer to [Suggested way to check credentials](https://restheart.org/docs/security/how-clients-authenticate/#suggested-way-to-check-credentials).