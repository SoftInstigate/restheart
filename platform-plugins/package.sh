#!/bin/bash

VERSION=$1

if [[ -z $VERSION ]]; then
    printf "ERROR: missing mandatory version.\n"
    exit 1
fi

TARGET="restheart-platform-$VERSION"
DIST="dist/$TARGET"

echo "###### Packaging RESTHeart Platform $VERSION"

echo "###### Copying files to $DIST"
rm -rf dist
mkdir -p "$DIST"
cp -Rv template/* "$DIST"
cp -v restheart-platform-core/target/restheart-platform-core.jar "$DIST"
cp -v restheart-platform-security/target/restheart-platform-security.jar "$DIST"
echo "###### ...Done"

echo "###### Compressing to archive '$TARGET.zip'"
cd dist || exit
zip -r "$TARGET.zip" "$TARGET" -x "*.DS_Store"
rm -rf "$TARGET"
cd ..
echo "###### ...Done."
