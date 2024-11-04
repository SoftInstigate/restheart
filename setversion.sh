#!/bin/bash

if [ -z "$1" ]; then
  cat <<EOF
This script is used to set the version of the project.
It only accepts release versions (not SNAPSHOT versions).
To create a new stable release of RESTHeart, for example 8.2.0:

   $ ./setversion.sh 8.2.0
   # This will automatically create a new release version and set the next version as a SNAPSHOT.

EOF
  exit 1
fi

# Enforce release version only (do not allow "SNAPSHOT" in the version)
if [[ "$1" == *SNAPSHOT* ]]; then
  echo "Error: SNAPSHOT versions are not allowed. Please provide a release version (e.g., 8.1.4)." >&2
  exit 1
fi

set -Eeuo pipefail

# Set the release version
mvn versions:set -DnewVersion="$1"

# Commit and tag the release version
echo "This is a Release"
git commit -am "Release version $1"
git tag "$1"

# Set the new version to SNAPSHOT (e.g., if input is 8.1.4, set version to 8.1.4-SNAPSHOT)
SNAPSHOT_VERSION="${1}-SNAPSHOT"
mvn versions:set -DnewVersion="$SNAPSHOT_VERSION"

# Commit the SNAPSHOT version
git commit -am "Bump version to $SNAPSHOT_VERSION [skip ci]"
