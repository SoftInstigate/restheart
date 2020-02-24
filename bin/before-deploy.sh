#!/bin/bash
set -e

echo "###### TRAVIS_PULL_REQUEST==$TRAVIS_PULL_REQUEST"
if [ "$TRAVIS_PULL_REQUEST" == 'false' ]; then
    openssl aes-256-cbc -K $encrypted_95e9394a014a_key -iv $encrypted_95e9394a014a_iv -in bin/codesigning.asc.enc -out bin/codesigning.asc -d
    export GPG_TTY=$(tty) // to avoid: gpg: signing failed: Inappropriate ioctl for device, ref: https://tutorials.technology/solved_errors/21-gpg-signing-failed-Inappropriate-ioctl-for-device.html
    gpg --batch --fast-import bin/codesigning.asc
fi