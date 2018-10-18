#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java -server -cp $DIR/../target/uiam.jar io.uiam.Shutdowner $1
sleep 2
