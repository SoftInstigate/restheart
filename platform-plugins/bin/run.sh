#!/bin/bash
set -e

cd "$(dirname ${BASH_SOURCE[0]})" || exit
pwd

check-mongod() {
    PID=$(pgrep -x mongod)
    if [[ -z $PID ]]; then
        echo "MongoDB doesn't seem to be running! Aborting."
        exit 1
    fi
}

open-browser() {
    # ...
    # Figure out how to invoke the current user's browser.
    # See: https://dwheeler.com/essays/open-files-urls.html
    viewer=FAIL
    for possibility in xdg-open gnome-open cygstart open start ; do
        if command -v "$possibility" >/dev/null 2>&1 ; then
            viewer="$possibility"
            break
        fi
    done
    if [ "$viewer" = FAIL ] ; then
        echo 'No viewer found.' >&2
    fi
    # Now $viewer is set, so we can use it.
    "$viewer" "$1"
}

check-mongod

echo "###### Starting restheart-platform ..."
java -Dfile.encoding=UTF-8 -server -jar restheart-platform-core.jar "etc/restheart-platform-core.yml" --envfile "etc/trial.properties" &

java -Dfile.encoding=UTF-8 -server -jar "restheart-platform-security.jar" "etc/restheart-platform-security.yml" &

sleep 9

open-browser http://localhost:8080/license
