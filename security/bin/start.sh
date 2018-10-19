#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java --illegal-access=deny -Dfile.encoding=UTF-8 -server -jar $DIR/../target/uiam.jar $1 --fork
echo 'Sleeping few seconds...'
sleep 2
