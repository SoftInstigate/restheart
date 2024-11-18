#!/bin/bash

# Display usage if no argument is provided
if [ -z "$1" ]; then
  cat <<EOF
This script sets the project version, commits the change, and optionally tags it.

Usage:
   ./setversion.sh <version> [--dry-run]

Options:
   --dry-run  Preview the changes without actually making them.

Examples:
   To create a new release:
       ./setversion.sh 8.1.4
   To set a new SNAPSHOT version:
       ./setversion.sh 8.1.5-SNAPSHOT
   To preview the changes:
       ./setversion.sh 8.1.4 --dry-run

After running the script:
   git push && git push --tags

EOF
  exit 1
fi

set -Eeuo pipefail

VERSION="$1"
DRY_RUN=false

# Check for dry-run option
if [[ "${2:-}" == "--dry-run" ]]; then
  DRY_RUN=true
fi

# Validate version format
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$ ]]; then
  echo "Error: Invalid version format. Use 'major.minor.patch' or 'major.minor.patch-SNAPSHOT'." >&2
  exit 1
fi

# Ensure git is configured
if ! git config user.name &>/dev/null || ! git config user.email &>/dev/null; then
  echo "Error: Git user.name and user.email are not configured." >&2
  exit 1
fi

# Dry-run output
if $DRY_RUN; then
  echo "Dry-run: The following actions would be performed:"
  echo "1. Set Maven version to $VERSION."
  if [[ "$VERSION" == *SNAPSHOT ]]; then
    echo "2. Commit the changes with message: 'Bump version to $VERSION [skip ci]'."
  else
    echo "2. Commit the changes with message: 'Release version $VERSION'."
    echo "3. Tag the release with '$VERSION'."
  fi
  echo "No changes were made."
  exit 0
fi

# Set Maven version
echo "Setting Maven version to $VERSION..."
mvn versions:set -DnewVersion="$VERSION"

# Commit and tag based on version type
if [[ "$VERSION" == *SNAPSHOT ]]; then
  echo "This is a SNAPSHOT version."
  git commit -am "Bump version to $VERSION [skip ci]"
else
  echo "This is a Release version."
  git commit -am "Release version $VERSION"
  git tag "$VERSION"
fi

echo "Version set to $VERSION successfully."
