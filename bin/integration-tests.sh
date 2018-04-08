#!/bin/bash
cd "$(dirname ${BASH_SOURCE[0]})"/.. || exit 1

echo "### Starting mongodb container..."
docker run --rm --name it-mongo -d -p 27017:27017 mongo:3.6

echo "### Build RESTHeart and run integration tests..."
mvn clean verify -DskipITs=false

echo "### Cleaning up..."
docker stop it-mongo

echo "### Done."
