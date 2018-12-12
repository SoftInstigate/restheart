# Testing

## Approach

uIAM testing leverages [RESTHeart](https://restheart.org) integration test suite that comprises more that 120 tests.

docker-compose is used to run the following application stack:

- uIAM
- RESTHeart
- MongoDB

RESTHeart is run with its security system disabled but uIAM runs in front of it to secure its resources. 

Once the stack is running, the RESTHeart integration test suite is run.

