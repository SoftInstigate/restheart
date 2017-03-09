#!/bin/bash
cd ..
git submodule update --init --recursive
mvn clean package -DskipTests=true
export RHVERSION=$(./project-version.sh)
echo "Building Docker image for RESTHeart Version "$RHVERSION
cd Docker
cp ../target/restheart.jar .
docker build -t restheart .
docker tag restheart restheart:$RHVERSION
rm restheart.jar
