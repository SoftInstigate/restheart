#!/usr/bin/env bash
# Runs the locally built RESTHeart (core/target) in RESTHeart's own base image,
# with the same JVM flags as core/Dockerfile. Usage: spike/run-rh.sh <name> [extra RESTHeart args]
set -euo pipefail
cd "$(dirname "$0")/.."
NAME=${1:-rh}; shift || true
mkdir -p ../cache-linux
docker rm -f "$NAME" >/dev/null 2>&1 || true
docker network inspect rhspike >/dev/null 2>&1 || docker network create rhspike >/dev/null
docker run -d --name "$NAME" --network rhspike -p 8080:8080 \
  -v "$PWD/core/target:/opt/restheart" -w /opt/restheart \
  -v "$PWD/../cache-linux:/cache" \
  -e RHO="${RHO:-/http-listener/host->\"0.0.0.0\"}" \
  ${DOCKER_EXTRA:-} \
  softinstigate/graalvm:25.1.3-graalce \
  -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
  -XX:MaxRAMPercentage=70.0 -XX:InitialRAMPercentage=60.0 -XX:+DisableExplicitGC \
  -XX:+ExitOnOutOfMemoryError -XX:+UseCompactObjectHeaders \
  -Dpolyglot.engine.userResourceCache=/cache ${JAVA_EXTRA:-} \
  -jar restheart.jar "$@" >/dev/null
