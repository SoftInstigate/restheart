#!/bin/bash
set -e

if [[ "$MAVEN_DEPLOY" == "true" ]]; then
    if [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
        RESTHEART_VERSION=$(./bin/project-version.sh 2>/dev/null);
        export RESTHEART_VERSION
        if [[ "$RESTHEART_VERSION" ]]; then
            mvn clean verify -DskipITs=false -Dkarate.options="$KARATE_OPS"
            echo "###### Building Docker image for RESTHeart $RESTHEART_VERSION";
            echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
            docker build -t softinstigate/restheart:$RESTHEART_VERSION . ;
            if [[ $RESTHEART_VERSION != *-SNAPSHOT ]]; then
                docker push softinstigate/restheart:$RESTHEART_VERSION;
            fi
            echo "###### Branch is $TRAVIS_BRANCH";
            if [ "$TRAVIS_BRANCH" == "master" ]; then
                echo "###### On master branch. Tagging image softinstigate/restheart:$RESTHEART_VERSION as latest";
                docker tag softinstigate/restheart:$RESTHEART_VERSION softinstigate/restheart:latest;
                docker push softinstigate/restheart:latest;
                echo "###### Publishing Maven site at http://softinstigate.github.io/restheart/project-info.html";
                mvn site --settings deploy-settings.xml -P report -Dmaven.test.skip=true;
            fi
            echo "###### Deploy to Maven Central"
            mvn deploy -s deploy-settings.xml -P release -Dmaven.test.skip=true
        else
            echo "###### ERROR! Variable RESTHEART_VERSION is undefined";
            exit 1;
        fi
    fi
else
    mvn verify -DskipITs=false
fi