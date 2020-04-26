# Run with Docker

RESTHeart public docker image is freely available on [Docker hub](https://hub.docker.com/r/softinstigate/restheart). Have a look at the [Dockerfile](../core/Dockerfile).

To run both RESTHeart and MongoDB services you can use `docker-compose`. Just copy and paste the following shell command:

```bash
curl https://raw.githubusercontent.com/SoftInstigate/restheart/master/docker-compose.yml --output docker-compose.yml && docker-compose up
```

You should see something similar to the following logs:

```
[...]
restheart    |  09:50:46.619 [main] INFO  o.r.mongodb.db.MongoClientSingleton - Connecting to MongoDB...
restheart-mongo | 2020-04-26T09:50:46.633+0000 I  NETWORK  [listener] connection accepted from 172.19.0.3:42898 #2 (2 connections now open)
restheart-mongo | 2020-04-26T09:50:46.635+0000 I  NETWORK  [conn2] received client metadata from 172.19.0.3:42898 conn2: { driver: { name: "mongo-java-driver|legacy", version: "3.11.2" }, os: { type: "Linux", name: "Linux", architecture: "amd64", version: "4.19.76-linuxkit" }, platform: "Java/Debian/11.0.6+10-post-Debian-1bpo91" }
restheart-mongo | 2020-04-26T09:50:46.636+0000 I  SHARDING [conn2] Marking collection admin.system.users as collection version: <unsharded>
restheart-mongo | 2020-04-26T09:50:46.870+0000 I  ACCESS   [conn2] Successfully authenticated as principal restheart on admin from client 172.19.0.3:42898
restheart    |  09:50:46.892 [main] INFO  o.r.mongodb.db.MongoClientSingleton - MongoDB version 4.2.1
restheart    |  09:50:46.893 [main] WARN  o.r.mongodb.db.MongoClientSingleton - MongoDB is a standalone instance.
restheart    |  09:50:47.156 [main] INFO  org.restheart.mongodb.MongoService - URI / bound to MongoDB resource /restheart
restheart    |  09:50:47.482 [main] INFO  org.restheart.Bootstrapper - HTTP listener bound at 0.0.0.0:8080
restheart    |  09:50:47.483 [main] DEBUG org.restheart.Bootstrapper - Content buffers maximun size is 16777216 bytes
restheart    |  09:50:47.498 [main] INFO  org.restheart.Bootstrapper - URI / bound to service mongo, secured: true
restheart    |  09:50:47.501 [main] INFO  org.restheart.Bootstrapper - URI /ic bound to service cacheInvalidator, secured: false
restheart    |  09:50:47.502 [main] INFO  org.restheart.Bootstrapper - URI /csv bound to service csvLoader, secured: false
restheart    |  09:50:47.503 [main] INFO  org.restheart.Bootstrapper - URI /roles bound to service roles, secured: true
restheart    |  09:50:47.504 [main] INFO  org.restheart.Bootstrapper - URI /ping bound to service ping, secured: false
restheart    |  09:50:47.506 [main] INFO  org.restheart.Bootstrapper - URI /tokens bound to service rndTokenService, secured: false
restheart    |  09:50:47.506 [main] DEBUG org.restheart.Bootstrapper - No proxies specified
restheart    |  09:50:47.515 [main] DEBUG org.restheart.Bootstrapper - Allow unescaped characters in URL: true
restheart    |  09:50:47.771 [main] INFO  org.restheart.Bootstrapper - Pid file /var/run/restheart-security--1411126229.pid
restheart    |  09:50:47.773 [main] INFO  org.restheart.Bootstrapper - RESTHeart started
```

Now point your browser to RESTHeart's [ping resource](http://localhost:8080/ping), you'll see the single line of text "__Greetings from RESTHeart!__".

Alternatively, use curl:

```http
$ curl -i http://localhost:8080/ping
HTTP/1.1 200 OK
Connection: keep-alive
Access-Control-Allow-Origin: *
X-Powered-By: restheart.org
Access-Control-Allow-Credentials: true
Access-Control-Expose-Headers: Location, ETag, Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location, X-Powered-By
Content-Length: 25
Date: Sun, 26 Apr 2020 10:05:43 GMT

Greetings from RESTHeart!
```

Press  `Ctrl+C` to stop the containers:

```
^CGracefully stopping... (press Ctrl+C again to force)
Stopping restheart       ... done
Stopping restheart-mongo ... done
```

If you want to run the services in background just add the `-d` parameter, like `docker-compose up -d`. In this case you can tail the logs with `docker-compose logs -f`. To stop the containers use `docker-compose stop` then `docker-compose start` to start them again. To completely shutdown the containers and clean-up everything use `docker-compose down -v`. Beware the `down` command with `-v` parameter erases the MongoDB attached docker volume (named `restheart-mongo-volume`) with all its data.

Read the [docker compose documentation](https://docs.docker.com/compose/) for more.

### Default users and ACL

The default `users.yml` defines the following users:

-   id: `admin`, password: `secret`, role: `admin`
-   id: `user`, password: `secret`, role: `user`

The default `acl.yml` defines the following permission:

-   _admin_ role can execute any request
-   _user_ role can execute any request on collection `/{username}`

### Check that everything works

```bash
# create database 'restheart'
$ curl --user admin:secret -I -X PUT :8080/
HTTP/1.1 201 OK

# create collection 'restheart.collection'
$ curl --user admin:secret -I -X PUT :8080/collection
HTTP/1.1 201 OK

# create a couple of documents
$ curl --user admin:secret -X POST :8080/collection -d '{"a":1}' -H "Content-Type: application/json"
$ curl --user admin:secret -X POST :8080/collection -d '{"a":2}' -H "Content-Type: application/json"

# get documents
$ curl --user admin:secret :8080/collection
[{"_id":{"$oid":"5dd3cfb2fe3c18a7834121d3"},"a":1,"_etag":{"$oid":"5dd3cfb2439f805aea9d5130"}},{"_id":{"$oid":"5dd3cfb0fe3c18a7834121d1"},"a":2,"_etag":{"$oid":"5dd3cfb0439f805aea9d512f"}}]%
```