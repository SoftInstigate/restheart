#!/bin/bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

while [[ $# -gt 0 ]]
do
   key="$1"
   if [ "${key:0:2}" == '-D' ] || [ "${key:0:2}" == '-X' ]; then
      SPROPS="$SPROPS $key"
   else
      ARGS="$ARGS $key"
   fi

   shift
done

java -Dfile.encoding=UTF-8 -server $SPROPS -jar "$DIR/../target/restheart-platform-security.jar" $@ $ARGS
echo 'Sleeping few seconds...'
sleep 2
