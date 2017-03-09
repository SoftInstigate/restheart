#!/bin/bash
cd ..
git submodule update --init --recursive
mvn package -DskipTests=true
cd Docker
cp ../target/restheart.jar .
docker build -t restheart-snapshot .
rm restheart.jar
