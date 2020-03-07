#!/bin/bash
set -e

echo "###### TRAVIS_PULL_REQUEST==$TRAVIS_PULL_REQUEST"
if [ "$TRAVIS_PULL_REQUEST" == 'false' ]; then
    openssl aes-256-cbc -K $encrypted_95e9394a014a_key -iv $encrypted_95e9394a014a_iv -in ./core/bin/codesigning.asc.enc -out ./core/bin/codesigning.asc -d
    gpg --pinentry-mode=loopback --batch --yes --fast-import ./core/bin/codesigning.asc
fi