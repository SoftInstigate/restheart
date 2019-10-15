#!/bin/bash

VERSION=$1

if [[ -z $VERSION ]]; then
    printf "ERROR: missing mandatory version.\n"
    exit 1
fi

DIST="restheart-platform-$VERSION"

echo "###### Packaging RESTHeart Platform $VERSION"

echo "###### Cleaning up $DIST"
rm -rf "$DIST" "$DIST.zip"
echo "######...Done."

echo "###### Copying files to $DIST"
cp -Rv template/ "$DIST"
cp -v restheart-platform-core/target/restheart-platform-core.jar "$DIST"
cp -v restheart-platform-security/target/restheart-platform-security.jar "$DIST"
echo "###### ...Done"

echo "###### Compressing to archive '$DIST.zip'"
zip -r "$DIST.zip" "$DIST" -x "*.DS_Store"
echo "###### ...Done."
