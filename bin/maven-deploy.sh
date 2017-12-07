#!/bin/bash
set -e

if [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
    mvn deploy --settings deploy-settings.xml -Dmaven.test.skip=true;
    export RESTHEART_VERSION=$(./bin/project-version.sh 2>/dev/null);
    if [[ $RESTHEART_VERSION ]]; then
        echo "Building Docker image for RESTHeart $RESTHEART_VERSION";
        docker login -u="$DOCKER_USER" -p="$DOCKER_PASS";
        cd Docker && cp ../target/restheart.jar . ;
        docker build -t softinstigate/restheart:$RESTHEART_VERSION . ;
        docker push softinstigate/restheart:$RESTHEART_VERSION;
        echo "Branch is $TRAVIS_BRANCH";
        if [ "$TRAVIS_BRANCH" == "master" ]; then
            echo "On master branch. Tagging image softinstigate/restheart:$RESTHEART_VERSION as latest";
            docker tag softinstigate/restheart:$RESTHEART_VERSION softinstigate/restheart:latest;
            docker push softinstigate/restheart:latest;
            echo "Publishing Maven site at http://softinstigate.github.io/restheart/project-info.html";
            cd .. && mvn site --settings deploy-settings.xml -P report -Dmaven.test.skip=true;
        fi
    else
        echo "ERROR! Variable RESTHEART_VERSION is undefined";
        exit 1;
    fi
fi