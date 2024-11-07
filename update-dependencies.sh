#!/bin/bash
# if the first argument is true, allow minor updates
ALLOW_MINOR_UPDATES=${1:-false}
echo "Updating dependencies with allowMinorUpdates=$ALLOW_MINOR_UPDATES"
mvn versions:use-latest-releases -DallowMinorUpdates=$ALLOW_MINOR_UPDATES -DincludePlugins=true
