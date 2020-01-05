#!/bin/bash
set -e

echo "###### Building Docker image..."

cd "$(dirname ${BASH_SOURCE[0]})"/..

git submodule update --init --recursive
mvn clean package
RHVERSION=$(./bin/project-version.sh)
export RHVERSION
echo "###### Building Docker image for restheart-platform-core $RHVERSION"
docker build -t softinstigate/restheart-platform-core .
docker tag softinstigate/restheart-platform-core "softinstigate/restheart-platform-core:$RHVERSION"
echo "###### Docker image successfully built."
