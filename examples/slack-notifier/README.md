# slackNotifierInterceptor interceptor

This plugin adds a response async interceptor that sends a message to a Slack channel when a document is created in a configurable collection.

It requires the following configuration:

```yml
slackNotifierInterceptor:
    channel: "your Slack channel id"
    oauth-token: "your Slack OAuth Token"
    db: restheart
    collection: contacts
```

You need to create a Slack app and generate an OAuth Token:
- [quick guide to create a Slack app](https://api.slack.com/start/overview#creating)
- [Setup permission and generate the OAuth Token](https://api.slack.com/messaging/sending#permissions)

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

**Important Configuration Step**: Before executing this script, ensure you have customized the `conf.yml` file with your Slack details. This configuration is crucial for the script to facilitate the sending of slack notifications.

## Testing the Service

Given that the collection is `contacts`

```bash
# create the collection
$ http -a admin:secret PUT :8080/contacts
# create a document, this sends the message to the configured Slack channel
$ http -a admin:secret :8080/contacts name=uji email=andra@softinstigate.com message="This is cool!"
```

A message should appear in your slack channel.
