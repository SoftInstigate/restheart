#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java -Xdebug -Xrunjdwp:transport=dt_socket,address=4000,server=y,suspend=n -Dfile.encoding=UTF-8 -server -jar $DIR/../target/restheart-platform-security.jar $1
echo 'Sleeping few seconds...'
sleep 2
