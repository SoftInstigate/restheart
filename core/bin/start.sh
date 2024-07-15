#!/bin/bash

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Run the fat jar
#java -Dfile.encoding=UTF-8 -server -jar "$DIR/../target/restheart.jar" $@

# run the regular jar with complete classpath
java -Dfile.encoding=UTF-8 -server \
    -cp "$DIR/../target/restheart-core.jar:$DIR/../target/lib/*:$DIR/../target/plugins/*" \
    org.restheart.Bootstrapper $@
