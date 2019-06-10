#!/bin/bash
set -e

VERSION=$1

if [[ -z $VERSION ]]; then
    printf "ERROR: missing mandatory version number.\n"
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

echo "Cleaning up..."
rm -rf "$DIST" && mkdir -p "$DIST"

echo "Copying files to $DIST..."
cp -v run.sh start-mongod.sh "$DIST" 
cp -v restheart-platform-core/target/restheart-platform-core.jar "$DIST"
cp -v restheart-platform-security/target/restheart-platform-security.jar "$DIST"
cp -vr restheart-platform-core/lickey "$DIST/lickey"
echo "...Done."

echo "Compressing to zip archive..."
zip -r "restheart-platform-$VERSION.zip" "$DIST"
echo "...Done."

