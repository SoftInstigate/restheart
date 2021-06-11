#!/bin/bash

source /root/.bashrc && java -Dfile.encoding=UTF-8 -server -jar /opt/restheart/restheart.jar /opt/restheart/etc/restheart.yml "$@"