#!/bin/bash
# Script to run Integration Tests with Maven and Junit.
# MongoDB is executed in a ephemeral Docker container.
set -e

#make sure the docker stop is called anyways, even if the tests do not run successfully
cleanup() {
    echo "### Cleaning up $IMAGE:$MONGO_VERSION container..."
    docker stop "$CONTAINER_ID"
    docker network rm "$MONGO_TMP_NETWORK"
    echo "### Done testing RESTHeart with $IMAGE:$MONGO_VERSION"
}
trap cleanup ERR INT TERM

# if you want to run integration tests against a different version of MongoDB
# export the MONGO_VERSION variable.
# For example, on a bash shell:
# $ export MONGO_VERSION=3.6 && ./bin/integration-tests.sh
if [[ -z $MONGO_VERSION ]]; then
    MONGO_VERSION=4.0
fi
if [[ -z $IMAGE ]]; then
    IMAGE=mongo
    # export IMAGE=percona/percona-server-mongodb (to run against Percona Server)
fi

cd "$(dirname ${BASH_SOURCE[0]})"/.. || exit 1

MONGO_TMP_NETWORK="mongo-$(date | md5)"

echo "### Running volatile $IMAGE:$MONGO_VERSION Docker container..."
docker network create "$MONGO_TMP_NETWORK"
CONTAINER_ID=$( docker run --rm -d -p 27017:27017 --net="$MONGO_TMP_NETWORK" --name mongo1 "$IMAGE:$MONGO_VERSION" --replSet rs0)
echo "Waiting for mongodb complete startup..." && sleep 10
docker run -it --rm --net="$MONGO_TMP_NETWORK" "$IMAGE:$MONGO_VERSION" mongo --host mongo1 --eval "rs.initiate()"

echo "### Build RESTHeart and run integration tests..."

case $MONGO_VERSION in
    *"4."*)
        KARATE_OPS=""
    ;;
    *"3.6"*)
        KARATE_OPS="--tags ~@requires-mongodb-4"
    ;;
    *)
        export KARATE_OPS="--tags ~@requires-mongodb-4 ~@requires-mongodb-3.6"
    ;;
esac

mvn clean verify -DskipITs=false -Dkarate.options="$KARATE_OPS"

cleanup