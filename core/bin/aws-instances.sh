#!/usr/bin/env bash
# instances.sh — list and terminate restheart native build instances
set -euo pipefail

REGION="${REGION:-eu-west-1}"

BOLD=$(tput bold 2>/dev/null || true)
RESET=$(tput sgr0 2>/dev/null || true)
GREEN=$(tput setaf 2 2>/dev/null || true)
YELLOW=$(tput setaf 3 2>/dev/null || true)
RED=$(tput setaf 1 2>/dev/null || true)
CYAN=$(tput setaf 6 2>/dev/null || true)

usage() {
  echo "Usage: $0 [list|terminate <instance-id>|terminate-all]"
  echo ""
  echo "  list             show all restheart-native-build instances"
  echo "  terminate <id>   terminate a specific instance"
  echo "  terminate-all    terminate all running build instances"
  exit 1
}

list_instances() {
  echo "${BOLD}RESTHeart native build instances — ${REGION}${RESET}"
  echo ""
  RESULT=$(aws ec2 describe-instances \
    --filters "Name=tag:Name,Values=restheart-native-build" \
    --query 'Reservations[].Instances[].[InstanceId,State.Name,InstanceType,Placement.AvailabilityZone,LaunchTime]' \
    --output text --region "${REGION}" 2>/dev/null)

  if [[ -z "${RESULT}" ]]; then
    echo "  (no instances found)"
    return
  fi

  printf "  %-22s %-14s %-14s %-14s %s\n" "Instance ID" "State" "Type" "AZ" "Launched"
  echo "  ────────────────────────────────────────────────────────────────────────"
  while IFS=$'\t' read -r id state type az launched; do
    case "${state}" in
      running)       color="${GREEN}" ;;
      shutting-down) color="${YELLOW}" ;;
      terminated)    color="${RESET}" ;;
      *)             color="${CYAN}" ;;
    esac
    printf "  %-22s ${color}%-14s${RESET} %-14s %-14s %s\n" \
      "${id}" "${state}" "${type}" "${az}" "${launched}"
  done <<< "${RESULT}"
  echo ""
}

terminate_instance() {
  local id="$1"
  echo "${YELLOW}Terminating ${id}...${RESET}"
  aws ec2 terminate-instances --instance-ids "${id}" --region "${REGION}" \
    --query 'TerminatingInstances[0].CurrentState.Name' --output text
  echo "${GREEN}✓ ${id} terminating${RESET}"
}

terminate_all() {
  IDS=$(aws ec2 describe-instances \
    --filters \
      "Name=tag:Name,Values=restheart-native-build" \
      "Name=instance-state-name,Values=pending,running,stopping,stopped" \
    --query 'Reservations[].Instances[].InstanceId' \
    --output text --region "${REGION}" 2>/dev/null)

  if [[ -z "${IDS}" ]]; then
    echo "No running build instances found."
    return
  fi

  echo "${YELLOW}Terminating: ${IDS}${RESET}"
  # shellcheck disable=SC2086
  aws ec2 terminate-instances --instance-ids ${IDS} --region "${REGION}" \
    --query 'TerminatingInstances[].[InstanceId,CurrentState.Name]' \
    --output table
  echo "${GREEN}✓ Done${RESET}"
}

CMD="${1:-list}"
case "${CMD}" in
  list)           list_instances ;;
  terminate)      [[ -z "${2:-}" ]] && usage; terminate_instance "$2" ;;
  terminate-all)  terminate_all ;;
  *)              usage ;;
esac
