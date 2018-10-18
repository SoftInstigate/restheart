#!/bin/bash
set -e

#make sure the docker stop is called anyways, even if the tests do not run successfully
cleanup() {
    echo "### Cleaning up $IMAGE:$MONGO_VERSION container..."
    docker stop "$CONTAINER_ID"

    echo "### Done testing uIAM with $IMAGE:$MONGO_VERSION"
}
trap cleanup ERR INT TERM

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

echo "### Build uIAM and run integration tests..."
mvn clean verify -DskipITs=false

cleanup