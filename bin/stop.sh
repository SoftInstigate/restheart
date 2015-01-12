#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java -server -cp $DIR/../target/restheart.jar com.softinstigate.restheart.Shutdowner $1
