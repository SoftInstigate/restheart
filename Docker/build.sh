#!/bin/bash
set -e

echo "###### start build.sh #####"

cd "$(dirname ${BASH_SOURCE[0]})"/..

git submodule update --init --recursive
mvn clean package
export RHVERSION=$(./bin/project-version.sh)
echo "###### Building Docker image for RESTHeart Version "$RHVERSION
docker build -t softinstigate/restheart .
docker tag softinstigate/restheart softinstigate/restheart:$RHVERSION
echo "###### end build.sh #####"
