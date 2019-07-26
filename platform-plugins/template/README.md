# RESTHeart Platform

## Run with Docker

```
$ docker-compose up -d
```

### Log files

You find the log files in the `restheart-platform-<version>` directory:

- core.log
- security.log

Standard Docker logs, also of the MongoDB instance, are available typing the following command:

```
$ docker-compose logs -f
```

### Accept License

This step is only required once on the first execution.

1. open <a href="http://localhost:8080/license" target="_blank">http://localhost:8080/license</a> (If you don't get any response wait few seconds for startup and retry)
2. add the license key copying it from the email and and pasting it in the _License Key_ field.

Once the license key has been added, you can accept it by checking the two checkboxes and clicking on **"Activate the License Key"** button.

### Check if the service is up

Open <a href="http://localhost:8080/roles/admin" target="_blank">http://localhost:8080/roles/admin</a>

Insert the default admin credentials, which are:

```properties
username: admin
password: secret
```

You should then see the following JSON in your browser:

```json
{ "authenticated": true, "roles": ["admin"] }
```

### Stop and restart the containers

1. Stop running Docker containers

```
$ docker-compose stop
```

2. Run again the existing Docker containers

```
$ docker-compose start
```

### Clean up everything

To stop and **permanently delete** all services, networks and disk volumes previously created:

```
$ docker-compose down -v
```

This command deletes all data in the MongoDB database!

Please refer to the [docker-compose official documentation](https://docs.docker.com/compose/reference/overview/) for more.

## Run without Docker

The steps about accepting the license are the [same as above]().

### Requirements

- Java 11 and later
- MongoDB

**Change Streams** require MongoDB v3.6 and later configured as replica set, **Transactions** require MongoDB v4.0 and later configured as Replica Set.

### Run MongoDB as a Replica Set

This section describes how to run MongoDB standalone configured as a Replica Set. Refer to the [MongoDB documentation](https://docs.mongodb.com/manual/tutorial/convert-standalone-to-replica-set/) for more information.

**Start MongoDb** passing the `replSet` option.

```
$ mongodb --fork --syslog --replSet foo
```

At the first run, the replica set must be initiated. Connect to MongoDB using the mongo shell:

```
$ mongo
```

Initiate the replica set as follows:

```
> rs.initiate()
```

### Start restheart-platform-core

```
$ java -jar restheart-platform-core.jar etc/restheart-platform-core.yml -e etc/default.properties
```

### Start restheart-platform-security

```
$ java -jar restheart-platform-security.jar etc/restheart-platform-security.yml
```

### Accept License

During the first execution you must accept the license as described [above](#accept-license).
