#!/bin/bash

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# stop the fat jar
java -server -cp "$DIR/../target/restheart-core.jar" org.restheart.Shutdowner $@

