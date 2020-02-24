#!/bin/bash
set -e

echo "###### TRAVIS_PULL_REQUEST==$TRAVIS_PULL_REQUEST"
if [ "$TRAVIS_PULL_REQUEST" == 'false' ]; then
    openssl aes-256-cbc -K $encrypted_95e9394a014a_key -iv $encrypted_95e9394a014a_iv -in bin/codesigning.asc.enc -out bin/codesigning.asc -d
    gpg --batch --no-tty --yes --fast-import bin/codesigning.asc
fi