# slackNotifierInterceptor interceptor

This plugin adds a response async interceptor that sends a message to a Slack channel when a document is created in a configurable collection.

It requires the following configuration:

```yml
plugins-args:
  slackNotifierInterceptor:
    channel: "your Slack channel id"
    oauth-token: "your Slack OAuth Token"
    db: restheart
    collection: contacts
```

You need to create a Slack app and generate an OAuth Token:

- [quick guide to create a Slack app](https://api.slack.com/start/overview#creating)
- [Setup permission and generate the OAuth Token](https://api.slack.com/messaging/sending#permissions)

## How to build and run

You need **JDK 17++** to build and run this example.

-   Clone this repo `git clone git@github.com:SoftInstigate/restheart-examples.git`
-   `cd` into the `restheart-examples` folder
-   [Download RESTHeart](https://github.com/SoftInstigate/restheart/releases/)
-   uncompress it: `unzip restheart.zip` or `tar -xvf restheart.tar.gz`.

### Run

1. `cd slack-notifier`
1. Build the plugin with `../mvnw package` (uses the maven-dependency-plugin to copy the jar of the external dependency to /target/lib)
1. Copy both the service JAR `target/slack-notifier.jar `and `target/lib/*` into `../restheart/plugins/` folder
1. Start MongoDB in your localhost.
1. cd into the restheart distribution you have previously downloaded and uncompressed.
1. add the above configuration snipped to `restheart/etc/restheart.yml` configuration file
1. Start the process: `java -jar restheart.jar etc/restheart.yml -e etc/default.properties`.

## Test

We suggest using [httpie](https://httpie.org) for calling the API from command line.

Given that the collection is `contacts`

```bash
# create the collection
$ http -a admin:secret PUT :8080/contacts
# create a document, this sends the message to the configured Slack channel
$ http -a admin:secret :8080/contacts name=uji email=andra@softinstigate.com message="This is cool!"
```

A message should appear in your slack channel.
