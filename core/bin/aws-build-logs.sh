#!/usr/bin/env bash
# build-logs.sh — stream the live build log of a running native build instance via SSM.
set -euo pipefail

REGION="${REGION:-eu-west-1}"

BOLD=$(tput bold 2>/dev/null || true)
RESET=$(tput sgr0 2>/dev/null || true)
GREEN=$(tput setaf 2 2>/dev/null || true)
CYAN=$(tput setaf 6 2>/dev/null || true)
YELLOW=$(tput setaf 3 2>/dev/null || true)

log()  { echo "${CYAN}[$(date '+%H:%M:%S')]${RESET} $*"; }
ok()   { echo "${GREEN}[$(date '+%H:%M:%S')] ✓ $*${RESET}"; }
warn() { echo "${YELLOW}[$(date '+%H:%M:%S')] ⚠ $*${RESET}"; }

# ── find running build instance ───────────────────────────────────────────────
INSTANCE_ID="${1:-}"

if [[ -z "${INSTANCE_ID}" ]]; then
  INSTANCE_ID=$(aws ec2 describe-instances \
    --filters \
      "Name=tag:Name,Values=restheart-native-build" \
      "Name=instance-state-name,Values=running,pending" \
    --query 'Reservations[0].Instances[0].InstanceId' \
    --output text --region "${REGION}" 2>/dev/null)

  if [[ -z "${INSTANCE_ID}" || "${INSTANCE_ID}" == "None" ]]; then
    warn "No running build instance found."
    echo "Usage: $0 [instance-id]"
    exit 1
  fi
  log "Found running instance: ${INSTANCE_ID}"
fi

# ── wait for SSM agent ────────────────────────────────────────────────────────
log "Waiting for SSM agent to connect..."
until aws ssm describe-instance-information \
    --filters "Key=InstanceIds,Values=${INSTANCE_ID}" \
    --query 'InstanceInformationList[0].InstanceId' \
    --output text --region "${REGION}" 2>/dev/null | grep -q "${INSTANCE_ID}"; do
  printf "."
  sleep 5
done
echo ""
ok "SSM connected — streaming /var/log/restheart-build.log"
echo "${BOLD}(Ctrl+C to detach — build continues in background)${RESET}"
echo ""

# ── stream log via SSM ────────────────────────────────────────────────────────
aws ssm start-session \
  --target "${INSTANCE_ID}" \
  --document-name AWS-StartInteractiveCommand \
  --parameters '{"command":["sudo tail -f /var/log/restheart-build.log"]}' \
  --region "${REGION}"
