#!/bin/bash
# if you want to run integration tests against a different version of MongoDB
# export the MONGO_VERSION variable.
# For example: export MONGO_VERSION=3.4 && ./bin/integration-tests.sh
if [[ -z $MONGO_VERSION ]]; then
    MONGO_VERSION=3.6
fi

cd "$(dirname ${BASH_SOURCE[0]})"/.. || exit 1

echo "### Running volatile mongo:$MONGO_VERSION Docker container..."
CONTAINER_ID=$( docker run --rm -d -p 27017:27017 mongo:"$MONGO_VERSION" )

echo "### Build RESTHeart and run integration tests..."
mvn clean verify -DskipITs=false

echo "### Cleaning up mongo:$MONGO_VERSION container..."
docker stop "$CONTAINER_ID"

echo "### Done."
