#!/bin/bash
set -e

if [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
    VERSION=$(./bin/project-version.sh 2>/dev/null);
    export VERSION
    if [[ "$VERSION" ]]; then
        mvn deploy --settings settings.xml -Dmaven.test.skip=true;
        echo "###### Branch is '$TRAVIS_BRANCH', Tag is '$TRAVIS_TAG', Version is '$VERSION'";
        # Build and push docker images only for releases
        if [[ "$TRAVIS_BRANCH" == "master" && "$TRAVIS_TAG" && "$VERSION" != *-SNAPSHOT ]]; then
            echo "###### Building Docker image for restheart-security $VERSION";
            echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
            docker build -t "softinstigate/restheart-security:$VERSION" . ;
            docker push "softinstigate/restheart-security:$VERSION";
            echo "###### Tagging image softinstigate/uiam:$VERSION as latest";
            docker tag "softinstigate/restheart-security:$VERSION" softinstigate/restheart-security:latest;
            docker push softinstigate/restheart-security:latest;
        else
            echo "###### Skipping Docker image build."
        fi
    else
        echo "###### ERROR! Variable VERSION is undefined";
        exit 1;
    fi
fi