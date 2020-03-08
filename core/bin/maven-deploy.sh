#!/bin/bash
set -e

RESTHEART_VERSION=$(./bin/project-version.sh 2>/dev/null)
export RESTHEART_VERSION
echo "RESTHEART_VERSION=${RESTHEART_VERSION}"

if [[ "$MAVEN_DEPLOY" == "true" && "$TRAVIS_PULL_REQUEST" == "false" ]]; then
    if [[ "$RESTHEART_VERSION" ]]; then
        mvn clean verify -DskipITs=false -Dkarate.options="$KARATE_OPS"
        echo "###### Branch is '$TRAVIS_BRANCH', Tag is '$TRAVIS_TAG', Version is '$RESTHEART_VERSION'"
        if [[ "$TRAVIS_TAG" && "$RESTHEART_VERSION" != *-SNAPSHOT ]]; then
            echo "###### Building Docker image for RESTHeart $RESTHEART_VERSION"
            echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
            docker build -t "softinstigate/restheart:$RESTHEART_VERSION" .
            if [[ "$RESTHEART_VERSION" != *-SNAPSHOT ]]; then
                docker push "softinstigate/restheart:$RESTHEART_VERSION"
            fi
            echo "###### On master branch. Tagging image softinstigate/restheart:$RESTHEART_VERSION as latest"
            docker tag "softinstigate/restheart:$RESTHEART_VERSION" softinstigate/restheart:latest
            docker push softinstigate/restheart:latest
            #echo "###### Publishing Maven site at http://softinstigate.github.io/restheart/project-info.html"
            #mvn site --settings deploy-settings.xml -P report -Dmaven.test.skip=true
        fi
        echo "###### Deploy to Maven Central"
        mvn deploy -s deploy-settings.xml -P release -Dmaven.test.skip=true
    else
        echo "###### ERROR! Variable RESTHEART_VERSION is undefined"
        exit 1
    fi
else
    mvn verify -DskipITs=false
fi