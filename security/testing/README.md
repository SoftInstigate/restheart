# Testing

## Approach

uIAM testing leverages [RESTHeart](https://restheart.org) integration test suite that comprises more that 120 tests.

docker-compose is used to run the following application stack:

- uIAM
- RESTHeart
- MongoDB

RESTHeart is run with its security system disabled but uIAM runs in front of it to secure its resources. 

Once the stack is running, the RESTHeart integration test suite is run.

## How to run the tests

> Note: **Stop local instances of MongoDb** before running test.sh. The MongoDb container exposes the default port. It is needed by the test logic to remove test data. If MongoDb is also running outside the container stack, it hides the container port.

First build the docker image:

```bash
$ ./Docker/build.sh
```

Then run the tests:

```bash
$ ./testing/test.sh
```

## Prerequisites

To run the test make sure the following tools are installed:

- java 11
- maven
- git
- docker
- docker compose