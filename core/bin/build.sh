#!/bin/bash

docker run -it --rm \
    -v "$PWD":/opt/app  \
    -v "$HOME"/.m2:/root/.m2 \
    softinstigate/graalvm-maven clean package