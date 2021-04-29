# Run RESTHeart on Node.js

RESTHeart can be executed on GraalVM's implementation of Node.js

This allows to develop plugins in JavaScript leveraging the Node runtime. For instance
it is possible to use the `http` Node module.

> This feature is experimental and not suggested for production.

## install GraalVM

(here we use the brilliant sdkman)

```bash
$ sdk install java 21.1.0.r16-grl
```

## install node

```bash
$ gu install nodejs
```

## run RESTHeart on Node

```bash
$ node --jvm --vm.cp=restheart.jar bin/restheart.js etc/restheart.yml -e etc/default.properties
```