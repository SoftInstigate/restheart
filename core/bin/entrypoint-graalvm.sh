#!/bin/bash

source /root/.bashrc && exec java -Dfile.encoding=UTF-8 -server -jar /opt/restheart/restheart.jar "$@"