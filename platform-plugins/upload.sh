#!/bin/bash

AWS_PROFILE=softinstigate

AWS_ACCESS_KEY_ID=$(aws configure get $AWS_PROFILE.aws_access_key_id)
export AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY=$(aws configure get $AWS_PROFILE.aws_secret_access_key)
export AWS_SECRET_ACCESS_KEY

echo "AWS_PROFILE=$AWS_PROFILE"
echo "AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID"
echo "AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY"

aws s3 cp dist/restheart-platform-*.zip s3://download.restheart.com

echo "Invalidate CloudFront cache for download.restheart.com"
aws configure set preview.cloudfront true
aws cloudfront create-invalidation --distribution-id "E1WE8DOGKDF0SO" --paths "/*"
