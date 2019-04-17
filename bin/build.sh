#!/bin/bash
set -e

if [[ "$REPLICASET" == "true" ]]; then
    mvn verify -DskipITs=false -Dkarate.options="--tags ~@requires-mongodb-4 ~@requires-replica-set"
else
    mvn clean verify -DskipITs=false
fi
