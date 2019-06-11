#!/bin/bash

check-mongod() {
    PID=$(pgrep -x mongod)
    if [[ -z $PID ]]; then
        echo "MongoDB doesn't seems to be running!"
    fi
}

check-mongod

MONGO_URI="mongodb://127.0.0.1:37017" java -Dfile.encoding=UTF-8 -server -jar "restheart-platform-core.jar" --fork

sleep 5

java -Dfile.encoding=UTF-8 -server -jar "restheart-platform-security.jar" --fork