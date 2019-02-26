#!/bin/bash
set -e

if [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
    mvn deploy --settings settings.xml -Dmaven.test.skip=true;
    VERSION=$(./bin/project-version.sh 2>/dev/null);
    export VERSION
    if [[ "$VERSION" ]]; then
        echo "###### Building Docker image for uIAM $VERSION";
        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
        docker build -t "softinstigate/uiam:$VERSION" . ;
        if [[ $VERSION != *-SNAPSHOT ]]; then
            docker push "softinstigate/uiam:$VERSION";
        fi
        echo "###### Branch is $TRAVIS_BRANCH";
        if [ "$TRAVIS_BRANCH" == "master" ]; then
            echo "###### On master branch. Tagging image softinstigate/uiam:$VERSION as latest";
            docker tag "softinstigate/uiam:$VERSION" softinstigate/uiam:latest;
            docker push softinstigate/uiam:latest;
            #echo "###### Publishing Maven site at http://softinstigate.github.io/uiam/project-info.html";
            #mvn site --settings deploy-settings.xml -P report -Dmaven.test.skip=true;
        fi
    else
        echo "###### ERROR! Variable VERSION is undefined";
        exit 1;
    fi
fi