# MongoDB serverStatus service

This service provides secure access to MongoDB's `serverStatus` diagnostic information through a REST API. It demonstrates best practices for creating administrative services in RESTHeart, including dependency injection, input validation, and security hardening.

## Features

- **Secure by default**: Requires authentication and validates all input
- **Configurable**: Can be customized via RESTHeart configuration
- **Safe**: Only allows `serverStatus` command with whitelisted options
- **Production-ready**: Includes proper error handling and logging

## Security

This service is marked as `secure=true`, requiring authentication. Additionally:

1. **Command Validation**: By default, custom commands are **disabled** to prevent command injection attacks
2. **Whitelist-based**: When enabled, only `serverStatus` with approved options is allowed
3. **Input Sanitization**: All command parameters are validated before execution
4. **Admin Database Only**: Executes commands only against the `admin` database

### Allowed serverStatus Options

When custom commands are enabled, only these options can be toggled (0 to exclude, 1 to include):

- `repl` - Replica set status
- `metrics` - Server metrics  
- `locks` - Lock information
- `network` - Network statistics
- `opcounters` - Operation counters
- `connections` - Connection information
- `memory` - Memory usage
- `asserts` - Assertion counters
- `extra_info` - Additional info
- `globalLock` - Global lock information
- `wiredTiger` - WiredTiger storage engine stats
- `tcmalloc` - TCMalloc memory allocator stats
- `storageEngine` - Storage engine info
- `indexStats` - Index statistics
- `sharding` - Sharding information
- `security` - Security statistics

## Configuration

Add to your `restheart.yml`:

```yaml
mongoServerStatus:
  # Enable custom commands (disabled by default for security)
  enable-custom-commands: false
  # Override the default URI (optional, defaults to /status/mongo)
  uri: /status/mongo
```

### Configuration Parameters

- **`enable-custom-commands`** (boolean, default: `false`): When `true`, allows filtering serverStatus output via the `command` query parameter. Disabled by default for security.
- **`uri`** (string, default: `/status/mongo`): Override the service URI. The default is set via `defaultURI` in the `@RegisterPlugin` annotation.

## Usage

**Default Operation:**

Get comprehensive server status (requires authentication):

```bash
http -a admin:secret GET :8080/status/mongo
```

**With Custom URI:**

If you've configured a custom URI in `restheart.yml`:

```yaml
mongoServerStatus:
  uri: /api/mongo-status
```

Then use:

```bash
http -a admin:secret GET :8080/api/mongo-status
```

**Custom Options (requires `enable-custom-commands: true`):**

Exclude replica set status, metrics, and locks:

```bash
http -a admin:secret GET ':8080/status/mongo?command={serverStatus:1,repl:0,metrics:0,locks:0}'
```

Exclude most sections to get a minimal response (keep only version, host, connections, etc.):

```bash
http -a admin:secret GET ':8080/status/mongo?command={serverStatus:1,repl:0,metrics:0,locks:0,opcounters:0,memory:0,globalLock:0,wiredTiger:0,tcmalloc:0}'
```

**Note:** MongoDB's `serverStatus` includes all sections by default. You can only **exclude** sections by setting them to `0`, not selectively include them. Setting a field to `1` (or omitting it) means it will be included in the output.

## Authorization Recommendations

For production deployments, create specific ACL rules to restrict access to this service:

```javascript
// In restheart.acl collection
{
  "roles": ["monitoring", "admin"],
  "predicate": "path-prefix('/status') and method(GET)",
  "priority": 100
}
```

Only grant access to trusted monitoring/admin roles, as serverStatus can reveal sensitive operational information.

## Further Reading

For details on MongoDB's `serverStatus` output, see: [MongoDB serverStatus Documentation](https://docs.mongodb.com/manual/reference/command/serverStatus/)

## Building the Plugin

Use the following command to build the plugin. Ensure you are in the project's root directory before executing it:

```bash
../mvnw clean package
```

## Running RESTHeart with the plugin

To run the RESTHeart with the plugin, use Docker as follows. This command maps the host's port 8080 to the container's port 8080 and mounts the build directory as a volume:

1) **Start MongoDB Container**

**For First-Time Setup:**

Use the following commands to start a MongoDB container and initialize it as a single node replica set.

```bash
docker run -d --name mongodb -p 27017:27017 mongo --replSet=rs0 # Launch a MongoDB container
docker exec mongodb mongosh --quiet --eval "rs.initiate()" # Initialize the MongoDB instance to work as a single node replica set
```

**For Subsequent Uses:**

If you've previously created the MongoDB container and just need to start it again, you can simply use the following command:

```bash
docker start mongodb
```

2) **Launch RESTHeart Container**

Run the RESTHeart container, linking it to the MongoDB container:

```bash
docker run --name restheart --rm -p "8080:8080"  -v ./target:/opt/restheart/plugins/custom softinstigate/restheart:latest
```

For more information see: [For development: run RESTHeart and open MongoDB with Docker](https://restheart.org/docs/setup-with-docker#for-development-run-restheart-and-open-mongodb-with-docker)

## Testing the Service

```bash
http -a admin:secret -b :8080/status/mongo

{
    "asserts": {
        "msg": 0,
        "regular": 0,
        "rollovers": 0,
        "user": 2,
        "warning": 0
    },
    "connections": {
        "active": 1,
        "available": 838858,
        "current": 2,
        "totalCreated": 2
    },
    "electionMetrics": {
        "averageCatchUpOps": 0.0,
        "catchUpTakeover": {
            "called": {
                "$numberLong": "0"
            },
            "successful": {
                "$numberLong": "0"
            }
        },
        "electionTimeout": {
            "called": {
                "$numberLong": "0"
            },
            "successful": {
                "$numberLong": "0"
            }
        },

    ...

    }
}

```
