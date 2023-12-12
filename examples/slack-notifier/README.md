# slackNotifierInterceptor interceptor

This plugin adds a response async interceptor that sends a message to a Slack channel when a document is created in a configurable collection.

It requires the following configuration in `restheart.yml`:

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


### Deploy

Copy both the service JAR `target/slack-notifier.jar `and `target/lib/*` into `../restheart/plugins/` folder

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
