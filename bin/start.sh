#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

pid=`ps ax | grep "java -server -jar" | grep '[r]esth' | awk 'NR==1{print $1}' | cut -d' ' -f1`
echo $pid
if [ -n "${pid}" ]; then
  echo RESTHeart is already running
else
  echo "starting RESTHeart"
  java -server -jar $DIR/../target/restheart.jar $DIR/../etc/restheart-integrationtest.yml &
  sleep 5
fi
