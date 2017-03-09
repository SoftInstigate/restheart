if [[ $RESTHEART_VERSION ]]; then
    echo "Building Docker image for RESTHeart $RESTHEART_VERSION";
    docker build -t softinstigate/restheart:$RESTHEART_VERSION . ;
    docker push softinstigate/restheart:$RESTHEART_VERSION;
    echo "Branch is $TRAVIS_BRANCH";
    if [ "$TRAVIS_BRANCH" == "master" ]; then
        echo "Tagging image softinstigate/restheart:$RESTHEART_VERSION as latest";
        docker tag softinstigate/restheart:$RESTHEART_VERSION softinstigate/restheart:latest;
        docker push softinstigate/restheart:latest;
    fi
else
    echo "Variable RESTHEART_VERSION undefined";
fi