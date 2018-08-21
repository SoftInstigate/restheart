#!/bin/bash
set -e

# if you want to run integration tests against a different version of MongoDB
# export the MONGO_VERSION variable.
# For example: export MONGO_VERSION=3.4 && ./bin/integration-tests.sh
if [[ -z $MONGO_VERSION ]]; then
    MONGO_VERSION=3.6
fi
if [[ -z $IMAGE ]]; then
    IMAGE=mongo
    # export IMAGE=percona/percona-server-mongodb (to run against Percona Server)
fi

cd "$(dirname ${BASH_SOURCE[0]})"/.. || exit 1

echo "### Running volatile $IMAGE:$MONGO_VERSION Docker container..."
CONTAINER_ID=$( docker run --rm -d -p 27017:27017 "$IMAGE:$MONGO_VERSION" )

echo "### Build RESTHeart and run integration tests..."
mvn clean verify -DskipITs=false

echo "### Cleaning up $IMAGE:$MONGO_VERSION container..."
docker stop "$CONTAINER_ID"

echo "### Done testing RESTHeart with $IMAGE:$MONGO_VERSION"
