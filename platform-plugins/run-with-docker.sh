#!/bin/bash

cd "$(dirname ${BASH_SOURCE[0]})" || exit
pwd

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

docker-compose up -d --build

printf "\n>>>>>>  Please wait for HTTP listeners ..... "

j=0
i=1
sp="/-\|"
echo -n ' '
while [[ "$(curl -s -o /dev/null -w ''%{http_code}'' http://localhost:8080/license)" != "302" ]]; do
    printf "\b${sp:i++%${#sp}:1}"
    j=$(( j + 1 ))
    sleep 1
done

printf "\n>>>>>>  Opening browser at http://localhost:8080/license ....\n"

open-browser http://localhost:8080/license

echo "Done!"
