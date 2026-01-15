#!/bin/bash

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Run the fat jar
# --enable-native-access=ALL-UNNAMED: Allow native access for GraalVM Truffle
# 2>/dev/null redirects stderr to suppress warnings (optional, remove if you want to see other warnings)
java -Dfile.encoding=UTF-8 \
     -server \
     --sun-misc-unsafe-memory-access=allow \
     --enable-native-access=ALL-UNNAMED \
     -jar "$DIR/../target/restheart.jar" $@ 2> >(grep -v "WARNING: A terminally deprecated method\|WARNING: sun.misc.Unsafe\|WARNING: Please consider reporting\|WARNING: will be removed\|WARNING: A restricted method\|WARNING: java.lang.System::load\|WARNING: Use --enable-native-access\|WARNING: Restricted methods will be blocked" >&2)
