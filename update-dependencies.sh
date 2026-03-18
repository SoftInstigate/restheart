#!/usr/bin/env bash

set -euo pipefail

# If the first argument is true, allow minor updates.
ALLOW_MINOR_UPDATES=${1:-false}
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
RULES_URI="file://${SCRIPT_DIR}/rules.xml"

if [[ -x "${SCRIPT_DIR}/mvnw" ]]; then
	MVN_CMD=("${SCRIPT_DIR}/mvnw")
else
	MVN_CMD=(mvn)
fi

COMMON_ARGS=(
	-DallowMinorUpdates="${ALLOW_MINOR_UPDATES}"
	-DincludePlugins=true
	-Dmaven.version.rules="${RULES_URI}"
)

echo "Updating dependencies with allowMinorUpdates=${ALLOW_MINOR_UPDATES}"

cd "${SCRIPT_DIR}"

# Versions Plugin goals that modify pom.xml are more reliable when run separately.
"${MVN_CMD[@]}" versions:use-latest-releases "${COMMON_ARGS[@]}"
"${MVN_CMD[@]}" versions:update-properties "${COMMON_ARGS[@]}"
