#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java -server -cp $DIR/../target/restheart-security.jar org.restheart.security.Shutdowner $1
sleep 2
