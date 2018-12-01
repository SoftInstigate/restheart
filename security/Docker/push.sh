#!/bin/bash
set -e

export VERSION=$(../bin/project-version.sh 2>/dev/null);

if [[ $VERSION ]]; then
    echo "Building Docker image for uIAM $VERSION";
    docker build -t softinstigate/uiam:VERSION . ;
    docker push softinstigate/uiam:$VERSION;
    echo "Branch is $TRAVIS_BRANCH";
    if [ "$TRAVIS_BRANCH" == "master" ]; then
        echo "Tagging image softinstigate/uiam:$VERSION as latest";
        docker tag softinstigate/uiam:$VERSION softinstigate/uiam:latest;
        docker push softinstigate/uiam:latest;
    fi
else
    echo "ERROR! Variable VERSION is undefined";
    exit 1;
fi