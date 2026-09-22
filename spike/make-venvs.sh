#!/usr/bin/env bash
# Creates one venv per Python plugin (plugin-dir/.venv) with GraalPy 25.1.3 on the
# JVM, inside RESTHeart's base image (shell variant), and pip-installs the plugin's
# requirements.txt. Paths inside the container match the RESTHeart container
# (/opt/restheart/plugins/<plugin>), so the venvs can be used as-is at runtime.
# usage: spike/make-venvs.sh <plugin-name> ...   (plugins already copied to core/target/plugins)
set -uo pipefail
cd "$(dirname "$0")/.."
mkdir -p ../cache-linux ../runs/venvs
for name in "$@"; do
  log=../runs/venvs/$name.log
  echo "== $name (log: $log)"
  /usr/bin/time -p docker run --rm --entrypoint "" \
    -v "$PWD/core/target:/opt/restheart" \
    -v "$PWD/spike/pylauncher/target/lib:/pylauncher/lib:ro" \
    -v "$PWD/spike/pylauncher/graalpy.sh:/pylauncher/graalpy.sh:ro" \
    -v "$PWD/../cache-linux:/cache" \
    softinstigate/graalvm:25.1.3-graalce-shell bash -c "
      set -x
      cd /opt/restheart/plugins/$name && rm -rf .venv &&
      /pylauncher/graalpy.sh -m venv .venv &&
      if [ -f requirements.txt ]; then .venv/bin/python -m pip install --disable-pip-version-check -r requirements.txt; fi;
      .venv/bin/python -m pip list --disable-pip-version-check
    " > "$log" 2>&1
  echo "exit=$?"; tail -15 "$log"
done
