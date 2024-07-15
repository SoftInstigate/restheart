#!/bin/bash

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# stop the fat jar
#java -server -cp "$DIR/../target/restheart.jar" org.restheart.Shutdowner $@

# stop the regular jar with complete classpath
java -server \
    -cp "$DIR/../target/restheart-core.jar:$DIR/../target/lib/*:$DIR/../target/plugins/*" \
    org.restheart.Shutdowner $@
