#!/bin/bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java -server -cp "$DIR/../target/restheart-platform-core.jar" org.restheart.Shutdowner $@
sleep 2
