#!/bin/bash
set -e

export VERSION=$(../bin/project-version.sh 2>/dev/null);

if [[ $VERSION ]]; then
    echo "Building Docker image for RESTHeart Security $VERSION";
    docker build -t softinstigate/restheart-security:VERSION . ;
    docker push softinstigate/restheart-security:$VERSION;
    echo "Branch is $TRAVIS_BRANCH";
    if [ "$TRAVIS_BRANCH" == "master" ]; then
        echo "Tagging image softinstigate/restheart-security:$VERSION as latest";
        docker tag softinstigate/restheart-security:$VERSION softinstigate/restheart-security:latest;
        docker push softinstigate/restheart-security:latest;
    fi
else
    echo "ERROR! Variable VERSION is undefined";
    exit 1;
fi