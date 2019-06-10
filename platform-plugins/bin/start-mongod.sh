#!/bin/bash

MONGOD=mongod
PORT=37017
MONGOD_DATA="${PWD}/mongodb_data/"
MONGOD_LOG="${PWD}/mongodb_log/mongodb.log"
MONGOD_PATH="$(command -v $MONGOD)"
RETVAL=0

check-mongod() {
    if ! [ -x "$MONGOD_PATH" ]; then
        echo "Error: $MONGOD is not installed. Aborting." >&2
        exit 1
    else
        echo "$MONGOD found in PATH at $MONGOD_PATH"
    fi
}

stop() {
    PID=$(pgrep -x "${MONGOD}")
    if [[ -n $PID ]]; then
        echo "Stopping MongoDB..."
        kill -15 "${PID}"
        RETVAL=$?
        echo "Done."
    else
        echo "Nothing to stop: MongoDB is not running."
    fi
}

start() {
    check-mongod
    PID=$(pgrep -x "${MONGOD}")
    if [[ -n $PID ]]; then
        echo "MongoDB is already running."
    else
        echo "Starting local MongoDB instance bound to 127.0.0.1 on port ${PORT}"
        mkdir mongodb_data
        mkdir mongodb_log
        eval "${MONGOD_PATH} --bind_ip 127.0.0.1 --port ${PORT} --dbpath ${MONGOD_DATA} --logpath ${MONGOD_LOG} --fork --logappend"
        RETVAL=$?
        echo "Done."
        echo "Check the logs at ${MONGOD_LOG}"
    fi
}

case "$1" in
    start)
        start
    ;;
    stop)
        stop
    ;;
    restart)
        stop
        start
    ;;
    *)
        echo $"Usage: $0 {start|stop|restart}"
        exit 1
esac

exit $RETVAL