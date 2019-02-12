#!/bin/bash

SPROPS="";
ARGS=""

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

java -Dfile.encoding=UTF-8 -server $SPROPS -jar /opt/restheart/restheart.jar $ARGS
