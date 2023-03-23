#!/bin/bash
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
