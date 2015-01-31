#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java -server -jar $DIR/../target/restheart.jar $1 --fork
echo 'Sleeping few seconds...'
sleep 2
