#!/bin/bash
set -e

export VERSION=$(../bin/project-version.sh 2>/dev/null);

if [[ $VERSION ]]; then
    echo "Building Docker image for restheart-platform-security $VERSION";
    docker build -t softinstigate/restheart-platform-security:VERSION . ;
    docker push softinstigate/restheart-platform-security:$VERSION;
    echo "Branch is $TRAVIS_BRANCH";
    if [ "$TRAVIS_BRANCH" == "master" ]; then
        echo "Tagging image softinstigate/restheart-platform-security:$VERSION as latest";
        docker tag softinstigate/restheart-platform-security:$VERSION softinstigaterestheart-platform-security:latest;
        docker push softinstigate/restheart-platform-security:latest;
    fi
else
    echo "ERROR! Variable VERSION is undefined";
    exit 1;
fi