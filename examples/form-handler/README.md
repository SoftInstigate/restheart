# Form Handler Service

An example service with a custom `Request` to handle Form POST using undertow `FormParser`

For example:

```bash
$ http --form :8080/formHandler name=Andrea nickname=uji
```

Returns

```json
{
    "name": "Andrea",
    "nickname": "uji"
}
```

## How to build and run

build

```
$ mvn clean package
```

deploy

```
$ cp target/form-handler.jar <restheart-dir>/plugins
```

then start, or restart RESTHeart!

you'll see the following startup log message, confirming that the plugin has been deployed.

```
10:48:13.584 [main] INFO  org.restheart.Bootstrapper - URI /formHandler bound to service formHandler, secured: false, uri match PREFIX
```