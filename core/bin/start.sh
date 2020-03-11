#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java -Dfile.encoding=UTF-8 -server -jar "$DIR/../target/restheart.jar" $@
echo 'Sleeping few seconds...'
sleep 3
