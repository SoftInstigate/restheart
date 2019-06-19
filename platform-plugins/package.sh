#!/bin/bash

VERSION=$1

if [[ -z $VERSION ]]; then
    printf "ERROR: missing mandatory version.\n"
    exit 1
fi

./build.sh

DIST="restheart-platform-$VERSION"

echo "###### Cleaning up..."
rm -rf "$DIST" "$DIST.zip"
echo "...Done."

echo "###### Copying files to $DIST..."
cp -Rv template/ "$DIST"
cp -v restheart-platform-core/target/restheart-platform-core.jar "$DIST/restheart-platform-core"
cp -v restheart-platform-security/target/restheart-platform-security.jar "$DIST/restheart-platform-security"
echo "...Done"

echo "###### Compressing to zip archive..."
zip -r "$DIST.zip" "$DIST"
echo "...Done."
