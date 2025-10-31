#!/usr/bin/env bash

# setversion.sh
# Safely set project version (Maven multi-module aware) with semver checks
# Usage: ./setversion.sh <version> [--dry-run] [--force]

set -Eeuo pipefail

print_usage() {
  cat <<EOF
This script sets the project version, commits the change, and optionally tags it.

Usage:
   ./setversion.sh <version> [--dry-run] [--force]

Options:
   --dry-run  Preview the changes without actually making them.
   --force    Force the change even if it looks like a downgrade or tag already exists.

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
}

if [ "$#" -lt 1 ]; then
  print_usage
  exit 1
fi

VERSION=""
DRY_RUN=false
FORCE=false

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    --force) FORCE=true; shift ;;
    -h|--help) print_usage; exit 0 ;;
    *)
       if [ -z "$VERSION" ]; then
         VERSION="$1"
         shift
       else
         echo "Unknown argument: $1" >&2
         print_usage
         exit 1
       fi
       ;;
  esac
done

# Basic semver validation (major.minor.patch) with optional -SNAPSHOT
if [[ ! "$VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-SNAPSHOT)?$ ]]; then
  echo "Error: Invalid version format. Use 'major.minor.patch' or 'major.minor.patch-SNAPSHOT'." >&2
  exit 1
fi

# helper: choose mvn wrapper if present
MVN_CMD="mvn"
if [ -x "$(pwd)/mvnw" ]; then
  MVN_CMD="$(pwd)/mvnw"
fi

# get current project version via maven (robust)
CURRENT_VERSION="$($MVN_CMD -q -DforceStdout help:evaluate -Dexpression=project.version 2>/dev/null || true)"
if [ -z "$CURRENT_VERSION" ]; then
  echo "Error: Unable to determine current project version via Maven ($MVN_CMD)." >&2
  exit 1
fi

echo "Current project version: $CURRENT_VERSION"

# parse semver parts
parse_semver() {
  local v="$1"
  if [[ "$v" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-SNAPSHOT)?$ ]]; then
    echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]} ${BASH_REMATCH[3]} ${BASH_REMATCH[4]:-}" # major minor patch suffix
  else
    return 1
  fi
}

compare_semver() {
  # returns: 1 if a>b, 0 if a==b, -1 if a<b
  local a=($1)
  local b=($2)
  for i in 0 1 2; do
    if (( ${a[i]} > ${b[i]} )); then
      echo 1; return 0
    elif (( ${a[i]} < ${b[i]} )); then
      echo -1; return 0
    fi
  done
  # numeric parts equal; treat SNAPSHOT as lower than release
  local sufA="${a[3]:-}"
  local sufB="${b[3]:-}"
  if [ "$sufA" = "$sufB" ]; then
    echo 0
  else
    # if a has -SNAPSHOT and b doesn't, a < b
    if [ "$sufA" = "-SNAPSHOT" ] && [ -z "$sufB" ]; then
      echo -1
    elif [ -z "$sufA" ] && [ "$sufB" = "-SNAPSHOT" ]; then
      echo 1
    else
      # fallback equal
      echo 0
    fi
  fi
}

CUR_PARTS=($(parse_semver "$CURRENT_VERSION")) || { echo "Error: current version $CURRENT_VERSION is not semver-compatible" >&2; exit 1; }
NEW_PARTS=($(parse_semver "$VERSION")) || { echo "Error: new version $VERSION is not semver-compatible" >&2; exit 1; }

cmp=$(compare_semver "${CUR_PARTS[*]}" "${NEW_PARTS[*]}") || true

if [ "$cmp" = "1" ]; then
  echo "Error: New version $VERSION is lower than current version $CURRENT_VERSION (potential downgrade)." >&2
  if [ "$FORCE" != "true" ]; then
    echo "Use --force to override." >&2
    exit 1
  else
    echo "--force provided: proceeding despite downgrade." >&2
  fi
elif [ "$cmp" = "0" ]; then
  echo "Warning: New version $VERSION is equal to current version $CURRENT_VERSION." >&2
  if [ "$FORCE" != "true" ]; then
    echo "Use --force to proceed anyway." >&2
    exit 1
  else
    echo "--force provided: proceeding." >&2
  fi
fi

  # Branch check: require branch like '<major>.x' (e.g. 8.x) unless --force
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || true)
  MAJOR="${NEW_PARTS[0]}"
  EXPECTED_BRANCH="${MAJOR}.x"
  if [ -n "$CURRENT_BRANCH" ] && [ "$CURRENT_BRANCH" != "$EXPECTED_BRANCH" ] && [ "$FORCE" != "true" ]; then
    echo "Error: Releasing version $VERSION requires being on branch '$EXPECTED_BRANCH' but current branch is '$CURRENT_BRANCH'." >&2
    echo "If you really want to proceed from this branch, pass --force." >&2
    exit 1
  fi

# Ensure git is configured
if ! git config user.name &>/dev/null || ! git config user.email &>/dev/null; then
  echo "Error: Git user.name and user.email are not configured." >&2
  exit 1
fi

# Ensure working tree is clean to avoid accidental overwrites (skip this check for --dry-run)
if [ "$DRY_RUN" != "true" ] && [ -n "$(git status --porcelain)" ] && [ "$FORCE" != "true" ]; then
  echo "Error: Working tree is not clean. Commit or stash changes, or pass --force to override." >&2
  git status --porcelain
  exit 1
fi

# Check if tag already exists (for release versions)
if [[ "$VERSION" != *SNAPSHOT ]]; then
  if git rev-parse -q --verify "refs/tags/$VERSION" >/dev/null; then
    echo "Error: Git tag '$VERSION' already exists." >&2
    if [ "$FORCE" != "true" ]; then
      echo "Use --force to overwrite or choose a different version." >&2
      exit 1
    else
      echo "--force provided: existing tag will be overwritten (old tag will be deleted and recreated)."
    fi
  fi
fi

# Dry-run summary
if [ "$DRY_RUN" = true ]; then
  echo "Dry-run: The following actions would be performed:"
  echo "  - Run: $MVN_CMD versions:set -DnewVersion=$VERSION -DprocessAllModules=true -DgenerateBackupPoms=false"
  if [[ "$VERSION" == *SNAPSHOT ]]; then
    echo "  - Commit with message: 'Bump version to $VERSION [skip ci]'"
  else
    echo "  - Commit with message: 'Release version $VERSION'"
    echo "  - Create git tag: $VERSION"
  fi
  if [ "$FORCE" = true ]; then
    echo "  - (force mode enabled)"
  fi
  echo "No changes were made."
  exit 0
fi

echo "Setting Maven version to $VERSION..."
$MVN_CMD versions:set -DnewVersion="$VERSION" -DprocessAllModules=true -DgenerateBackupPoms=false

# Commit changes
if [[ "$VERSION" == *SNAPSHOT ]]; then
  echo "This is a SNAPSHOT version. Committing bump..."
  git add -A
  git commit -m "Bump version to $VERSION [skip ci]"
else
  echo "This is a Release version. Committing and tagging..."
  git add -A
  git commit -m "Release version $VERSION"
  # if tag exists and force, delete it first
  if git rev-parse -q --verify "refs/tags/$VERSION" >/dev/null; then
    if [ "$FORCE" = "true" ]; then
      git tag -d "$VERSION" || true
      git push --delete origin "$VERSION" || true
    fi
  fi
  git tag "$VERSION"
fi

echo "Version set to $VERSION successfully."
