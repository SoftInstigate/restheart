#!/bin/bash
set -e

if [[ "$REPLICASET" == "true" ]]; then
    docker network create mongo-cluster
    docker run -d -p 27017:27017 --net=mongo-cluster --name mongo1 mongo:$MONGO --replSet rs0
    echo "Waiting for mongodb complete startup..." && sleep 10
    docker run -it --rm --net=mongo-cluster mongo:"$MONGO" mongo --host mongo1 --eval "rs.initiate()"
else
    docker run -d -p 27017:27017 mongo:"$MONGO"
fi