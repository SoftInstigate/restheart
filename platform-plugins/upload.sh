#!/bin/bash
set -e

VERSION=$1

if [[ -z $VERSION ]]; then
    printf "ERROR: missing mandatory version.\n"
    exit 1
fi

AWS_PROFILE=softinstigate

AWS_ACCESS_KEY_ID=$(aws configure get $AWS_PROFILE.aws_access_key_id)
export AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY=$(aws configure get $AWS_PROFILE.aws_secret_access_key)
export AWS_SECRET_ACCESS_KEY

echo "AWS_PROFILE=$AWS_PROFILE"
echo "AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID"
echo "AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY"

DIST=restheart-platform-$VERSION

echo "###### Cleaning up..."
rm -rf "$DIST" "$DIST.zip"
mkdir -p "$DIST"
echo "...Done."

echo "###### Copying files to $DIST..."
cp -v bin/run.sh bin/mongod.sh "$DIST"
cp -v restheart-platform-core/target/restheart-platform-core.jar "$DIST"
cp -v restheart-platform-security/target/restheart-platform-security.jar "$DIST"
mkdir -p "$DIST/etc/"
cp -v trial/* "$DIST/etc/"
mkdir -p "$DIST/lickey/"
cp -Rv restheart-platform-core/lickey/COMM-LICENSE.txt "$DIST/lickey/"
echo "...Done"

echo "###### Compressing to zip archive..."
zip -r "$DIST.zip" "$DIST"
echo "...Done."
