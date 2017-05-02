#!/bin/bash
set -e

echo "###### start build.sh #####"

PRESENT_DIR="$PWD";
cd "$(dirname ${BASH_SOURCE[0]})"/..

git submodule update --init --recursive
mvn clean package -DskipTests=true
export RHVERSION=$(./bin/project-version.sh)
echo "###### Building Docker image for RESTHeart Version "$RHVERSION
cd Docker
cp ../target/restheart.jar .
docker build -t restheart .
docker tag restheart restheart:$RHVERSION
rm restheart.jar
cd "$PRESENT_DIR"
echo "###### end build.sh #####"
