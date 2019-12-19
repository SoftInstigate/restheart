#!/bin/bash

VERSION=$1

if [[ -z $VERSION ]]; then
    printf "ERROR: missing mandatory version.\n"
    exit 1
fi

TARGET="restheart-platform-$VERSION"
DIST="dist/$TARGET"

echo "###### Packaging RESTHeart Platform $VERSION"

echo "###### Copying files to template/etc"

rm -rf template/etc
mkdir template/etc
cp -v restheart-platform-core/etc/restheart-platform-core.yml template/etc
cp -v restheart-platform-core/etc/core.properties template/etc
cp -v restheart-platform-core/etc/core-docker.properties template/etc
cp -v restheart-platform-core/etc/core-bwcv3.properties template/etc
cp -v restheart-platform-core/etc/core-standalone.properties template/etc
cp -v restheart-platform-security/etc/restheart-platform-security.yml template/etc
cp -v restheart-platform-security/etc/security-docker.properties template/etc
cp -v restheart-platform-security/etc/security.properties template/etc

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
