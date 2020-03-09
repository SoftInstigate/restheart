# RESTHeart Security end to end test

## Approach

Execute the [RESTHeart](https://restheart.org) integration test suite on the following stack:

- RESTHeart Security
- RESTHeart
- MongoDB

## How to run the tests

> Note: **Stop local instances of MongoDb** before running test.sh. The MongoDb container exposes the default port. It is needed by the test logic to remove test data. If MongoDb is also running outside the container stack, it hides the container port.

First build the docker image:

```bash
$ ./Docker/build.sh
```

Then run the tests:

```bash
$ ./e2e-test/go.sh
```

## Prerequisites

To run the test make sure the following tools are installed:

- java 11
- maven
- git
- docker
- docker compose