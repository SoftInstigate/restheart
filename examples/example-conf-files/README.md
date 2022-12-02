This directory contains some example configuration files.

| configuration file        | description | how to use|see `
|---------------------------|-------------|-------------|-----|
| `acl.json`                | Access Control List for `mongoAclAuthorizer`| To be loaded into the `acl` collection via `POST /acl` request | [Mongo ACL Authorizer](https://restheart.org/docs/security/authorization#mongo-acl-authorizer)
| `acl.yml`                 | Access Control List for `fileAclAuthorizer`|To be referred by the configuration option `/fileAclAuthorizer/conf-file`|[File ACL Authorizer](https://restheart.org/docs/security/authorization#file-acl-authorizer)
| `users.yml`               | User definition file for `fileRealmAuthenticator` |To be referred by the configuration option `/fileRealmAuthenticator/conf-file`| [Configuration](https://restheart.org/docs/configuration)|
| `conf-override.conf`      | Configuration override file with the same format of the RHO env variable |`java -jar restheart.jar -o conf.override.conf`|[Configuration](https://restheart.org/docs/configuration)|
| `conf-override.jsonc`     | Configuration override file in JSON with Comments format|`java -jar restheart.jar -o conf.override.jsonc`|[Configuration](https://restheart.org/docs/configuration)|
| `conf-override.yml`       | Configuration override file in YML format |`java -jar restheart.jar -o conf.override.yml`|[Configuration](https://restheart.org/docs/configuration)|

