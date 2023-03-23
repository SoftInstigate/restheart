#!/bin/bash
if [ -z "$1" ]
then
cat << EOF
This script is used to set the version of the project.
It also commits the change and creates a tag if the version is not a SNAPSHOT.
To create a new stable release of RESTHeart, for example 7.3.4:

   $ ./setversion.sh 7.3.4
   $ ./setversion.sh 7.3.5-SNAPSHOT
   $ git push && git push --tags

EOF
exit 1
fi

set -Eeuo pipefail

mvn versions:set -DnewVersion="$1"

if [[ "$1" == *SNAPSHOT ]]
then
  echo "This is a SNAPSHOT";
  git commit -am "Bump version to $1 [skip ci]"
else
  echo "This is a Release";
  git commit -am "Release version $1"
  git tag "$1"
fi
