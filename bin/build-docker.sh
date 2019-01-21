#!/bin/bash
set -e

echo "###### Building Docker image..."

cd "$(dirname ${BASH_SOURCE[0]})"/..

git submodule update --init --recursive
mvn clean package
RHVERSION=$(./bin/project-version.sh)
export RHVERSION
echo "###### Building Docker image for RESTHeart Version $RHVERSION"
docker build -t softinstigate/restheart .
docker tag softinstigate/restheart "softinstigate/restheart:$RHVERSION"
echo "###### Docker image successfully built."
