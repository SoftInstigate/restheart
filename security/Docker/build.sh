#!/bin/bash
set -e

echo "###### start build.sh #####"

cd "$(dirname ${BASH_SOURCE[0]})"/..

mvn clean package
export VERSION=$(./bin/project-version.sh)
echo "###### Building Docker image for uIAM Version "$VERSION
docker build -t softinstigate/uiam .
docker tag softinstigate/uiam softinstigate/uiam:$VERSION
echo "###### end build.sh #####"
