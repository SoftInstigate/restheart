#!/bin/bash
set -e

echo "###### start build.sh #####"

cd "$(dirname ${BASH_SOURCE[0]})"/..

mvn clean package
export VERSION=$(./bin/project-version.sh)
echo "###### Building Docker image for RESTHeart Security Version "$VERSION
docker build -t softinstigate/restheart-security .
docker tag softinstigate/restheart-security softinstigate/restheart-security:$VERSION
echo "###### end build.sh #####"
