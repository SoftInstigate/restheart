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

java -Dfile.encoding=UTF-8 --illegal-access=deny -server $SPROPS -jar /opt/uiam/uiam.jar $ARGS