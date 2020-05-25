#!/bin/bash
set -e

echo "###### Building Docker image..."

cd "$(dirname ${BASH_SOURCE[0]})"/..

git submodule update --init --recursive
mvn clean package
RHVERSION=$(./bin/project-version.sh)
export RHVERSION
echo "###### Building Docker image for restheart-platform-security Version $RHVERSION"
docker build -t softinstigate/restheart-platform-security .
docker tag softinstigate/restheart-platform-security "softinstigate/restheart-platform-security:$RHVERSION"
echo "###### Docker image successfully built."
