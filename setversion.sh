#!/bin/bash
set -Eeuo pipefail

echo "Setting new version to $1"

on_error() {
    echo "Reverting to $VERSION"
    mvn versions:set -DnewVersion="$VERSION"
    exit 1
}

trap on_error ERR

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
echo "Old version was $VERSION"

mvn versions:set -DnewVersion="$1"
mvn clean compile
#git commit -am "Release $1"