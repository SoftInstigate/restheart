#!/bin/bash
set -e

PRESENT_DIR="$PWD";
cd "$(dirname ${BASH_SOURCE[0]})"/.. || exit 1
MVN_VERSION=$(mvn --quiet \
    -Dexec.executable="echo" \
    -Dexec.args='${project.version}' \
    --non-recursive \
    org.codehaus.mojo:exec-maven-plugin:1.3.1:exec 2>/dev/null)
export MVN_VERSION
echo "$MVN_VERSION"
cd "$PRESENT_DIR" || exit 2
