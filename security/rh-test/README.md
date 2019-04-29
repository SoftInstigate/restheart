# RESTHeart Test

## Approach

Execute the [RESTHeart](https://restheart.org) integration test suite that comprises more that 120 tests on the following application stack:

- uIAM
- RESTHeart
- MongoDB

RESTHeart is run with its security system disabled but uIAM runs in front of it to secure its resources. 

## How to run the tests

> Note: **Stop local instances of MongoDb** before running test.sh. The MongoDb container exposes the default port. It is needed by the test logic to remove test data. If MongoDb is also running outside the container stack, it hides the container port.

First build the docker image:

```bash
$ ./Docker/build.sh
```

Then run the tests:

```bash
$ ./rh-test/go.sh
```

## Prerequisites

To run the test make sure the following tools are installed:

- java 11
- maven
- git
- docker
- docker compose