#!/bin/bash

SLEEP_TIME=6

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java -Dfile.encoding=UTF-8 -server -jar "$DIR/../target/restheart.jar" $@

echo "Sleeping ${SLEEP_TIME} seconds..."
sleep ${SLEEP_TIME}