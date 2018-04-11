#!/bin/bash

if [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
    mvn deploy --settings deploy-settings.xml -P release -Dmaven.test.skip=true;
    RESTHEART_VERSION=$(./bin/project-version.sh 2>/dev/null);
    export RESTHEART_VERSION
    if [[ "$RESTHEART_VERSION" ]]; then
        echo "###### Building Docker image for RESTHeart $RESTHEART_VERSION";
        docker login -u="$DOCKER_USER" -p="$DOCKER_PASS";
        docker build -t softinstigate/restheart:$RESTHEART_VERSION . ;
        docker push softinstigate/restheart:$RESTHEART_VERSION;
        echo "###### Branch is $TRAVIS_BRANCH";
        if [ "$TRAVIS_BRANCH" == "master" ]; then
            echo "###### On master branch. Tagging image softinstigate/restheart:$RESTHEART_VERSION as latest";
            docker tag softinstigate/restheart:$RESTHEART_VERSION softinstigate/restheart:latest;
            docker push softinstigate/restheart:latest;
            echo "###### Publishing Maven site at http://softinstigate.github.io/restheart/project-info.html";
            mvn site --settings deploy-settings.xml -P report -Dmaven.test.skip=true;
        fi
    else
        echo "###### ERROR! Variable RESTHEART_VERSION is undefined";
        exit 1;
    fi
fi