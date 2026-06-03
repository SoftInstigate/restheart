#!/usr/bin/env bash
# build-native.sh — launch a spot EC2 instance, build restheart native binary, stream logs, print cost.
set -euo pipefail

# ── config (override via env vars) ───────────────────────────────────────────
KEY_NAME="${KEY_NAME:-}"          # optional — not needed, instance self-terminates
INSTANCE_TYPE="${INSTANCE_TYPE:-c5.4xlarge}"
REGION="${REGION:-eu-west-1}"
S3_BUCKET="${S3_BUCKET:-softinstigate-restheart-builds/native}"
BRANCH="${BRANCH:-9.x}"
IAM_PROFILE="${IAM_PROFILE:-restheart-build-role}"
GRAALVM_VERSION="${GRAALVM_VERSION:-25.0.2-graalce}"
WITH_TEST_PLUGINS=false
POLL_INTERVAL=15

# ── parse args ────────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    -t|--test-plugins) WITH_TEST_PLUGINS=true; shift ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done


# ── colors ────────────────────────────────────────────────────────────────────
BOLD=$(tput bold 2>/dev/null || true)
RESET=$(tput sgr0 2>/dev/null || true)
GREEN=$(tput setaf 2 2>/dev/null || true)
YELLOW=$(tput setaf 3 2>/dev/null || true)
CYAN=$(tput setaf 6 2>/dev/null || true)

log()  { echo "${CYAN}[$(date '+%H:%M:%S')]${RESET} $*"; }
ok()   { echo "${GREEN}[$(date '+%H:%M:%S')] ✓ $*${RESET}"; }
warn() { echo "${YELLOW}[$(date '+%H:%M:%S')] ⚠ $*${RESET}"; }

# ── IAM helpers ───────────────────────────────────────────────────────────────
# Policy is always re-applied so it stays in sync with the current S3_BUCKET.
# policy built once after S3_BUCKET is known
IAM_POLICY="{ \"Version\":\"2012-10-17\", \"Statement\":[ {\"Effect\":\"Allow\",\"Action\":[\"s3:PutObject\",\"s3:GetObject\"],\"Resource\":\"arn:aws:s3:::${S3_BUCKET%%/*}/*\"}, {\"Effect\":\"Allow\",\"Action\":\"s3:ListBucket\",\"Resource\":\"arn:aws:s3:::${S3_BUCKET%%/*}\"}, {\"Effect\":\"Allow\",\"Action\":\"ec2:TerminateInstances\",\"Resource\":\"*\"} ] }"

print_iam_setup() {
  echo ""
  warn "Run these commands manually to create the required IAM role:"
  echo "  aws iam create-role --role-name ${IAM_PROFILE} --assume-role-policy-document '{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"ec2.amazonaws.com\"},\"Action\":\"sts:AssumeRole\"}]}'"
  echo "  aws iam put-role-policy --role-name ${IAM_PROFILE} --policy-name restheart-build-policy --policy-document '${IAM_POLICY}'"
  echo "  aws iam create-instance-profile --instance-profile-name ${IAM_PROFILE}"
  echo "  aws iam add-role-to-instance-profile --instance-profile-name ${IAM_PROFILE} --role-name ${IAM_PROFILE}"
  echo ""
}

apply_iam_policy() {
  aws iam put-role-policy \
    --role-name "${IAM_PROFILE}" \
    --policy-name restheart-build-policy \
    --policy-document "${IAM_POLICY}" \
    || { warn "Failed to update IAM policy."; print_iam_setup; exit 1; }
  ok "IAM policy synced -> s3://${S3_BUCKET%%/*}"
}

ensure_iam_profile() {
  log "Checking IAM instance profile '${IAM_PROFILE}'..."
  if aws iam get-instance-profile --instance-profile-name "${IAM_PROFILE}" &>/dev/null; then
    ok "IAM instance profile '${IAM_PROFILE}' exists."
    apply_iam_policy  # always keep policy in sync with current S3_BUCKET
    return
  fi

  warn "Profile not found — creating IAM role and instance profile..."
  aws iam create-role \
    --role-name "${IAM_PROFILE}" \
    --assume-role-policy-document \
    '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}' \
    || { warn "Failed to create role."; print_iam_setup; exit 1; }

  apply_iam_policy

  aws iam attach-role-policy \
    --role-name "${IAM_PROFILE}" \
    --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore \
    2>/dev/null || true

  aws iam create-instance-profile \
    --instance-profile-name "${IAM_PROFILE}" 2>/dev/null || true

  aws iam add-role-to-instance-profile \
    --instance-profile-name "${IAM_PROFILE}" \
    --role-name "${IAM_PROFILE}" \
    || { warn "Failed to attach role to profile."; print_iam_setup; exit 1; }

  log "Waiting for IAM profile to propagate..."
  sleep 15
  ok "IAM instance profile '${IAM_PROFILE}' ready."
}

# ── 1. IAM ────────────────────────────────────────────────────────────────────
ensure_iam_profile

# ── validate S3 bucket ───────────────────────────────────────────────────────
BUCKET_NAME="${S3_BUCKET%%/*}"
if ! aws s3 ls "s3://${BUCKET_NAME}" --region "${REGION}" &>/dev/null; then
  warn "Bucket '${BUCKET_NAME}' not found or not accessible. Creating it..."
  aws s3 mb "s3://${BUCKET_NAME}" --region "${REGION}" \
    || { warn "Failed to create bucket. Set S3_BUCKET=your-bucket/path and retry."; exit 1; }
  ok "Bucket '${BUCKET_NAME}' created."
fi

# ── S3 lifecycle: auto-delete build logs after 30 days (one-time setup) ──────────
if ! aws s3api get-bucket-lifecycle-configuration \
    --bucket "${BUCKET_NAME}" --region "${REGION}" &>/dev/null; then
  aws s3api put-bucket-lifecycle-configuration \
    --bucket "${BUCKET_NAME}" \
    --lifecycle-configuration "{
      \"Rules\": [
        {
          \"ID\": \"delete-old-build-logs\",
          \"Filter\": {\"Prefix\": \"${S3_BUCKET#*/}/build-\"},
          \"Status\": \"Enabled\",
          \"Expiration\": {\"Days\": 30}
        },
        {
          \"ID\": \"expire-maven-cache\",
          \"Filter\": {\"Prefix\": \"m2-cache/\"},
          \"Status\": \"Enabled\",
          \"Expiration\": {\"Days\": 30}
        },
        {
          \"ID\": \"expire-graalvm-cache\",
          \"Filter\": {\"Prefix\": \"graalvm-cache/\"},
          \"Status\": \"Enabled\",
          \"Expiration\": {\"Days\": 30}
        }
      ]
    }" && log "S3 lifecycle configured: build logs, maven cache, graalvm cache - all 30d." || true
fi

# ── 2. resolve latest Amazon Linux 2023 AMI ───────────────────────────────────
log "Resolving latest Amazon Linux 2023 x86_64 AMI..."
AMI_ID=$(aws ec2 describe-images \
  --owners amazon \
  --filters 'Name=name,Values=al2023-ami-2023*' 'Name=architecture,Values=x86_64' \
  --query 'sort_by(Images, &CreationDate)[-1].ImageId' \
  --output text --region "${REGION}")
log "AMI: ${AMI_ID}"

# ── 3. user-data ─────────────────────────────────────────────────────────────
BUILD_LOG_KEY="${S3_BUCKET}/build-$(date -u '+%Y%m%d-%H%M%S').log"

USER_DATA=$(cat <<SCRIPT
#!/bin/bash
LOG=/var/log/restheart-build.log
exec > >(tee -a \$LOG) 2>&1

self_terminate() {
  echo "=== Uploading build log to S3 ==="
  aws s3 cp \$LOG "s3://${BUILD_LOG_KEY}" --region "${REGION}" 2>/dev/null || true
  TOKEN=\$(curl -sX PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
  ISELF=\$(curl -s -H "X-aws-ec2-metadata-token: \$TOKEN" http://169.254.169.254/latest/meta-data/instance-id)
  aws ec2 terminate-instances --instance-ids "\$ISELF" --region "${REGION}" || true
}
trap self_terminate EXIT
set -e

echo "=== RESTHeart native build ==="
echo "Branch: ${BRANCH}  GraalVM: ${GRAALVM_VERSION}"
yum install -y git gcc glibc-devel zlib-devel

echo "=== Installing GraalVM ${GRAALVM_VERSION} ==="
export HOME=/root SDKMAN_DIR="/root/.sdkman"
GRAALVM_CACHE="s3://${S3_BUCKET%%/*}/graalvm-cache/${GRAALVM_VERSION}.tar.gz"

if aws s3 ls "\$GRAALVM_CACHE" --region "${REGION}" &>/dev/null; then
  echo "Restoring GraalVM from S3 cache..."
  aws s3 cp "\$GRAALVM_CACHE" /tmp/graalvm.tar.gz --region "${REGION}"
  mkdir -p "\$SDKMAN_DIR/candidates/java"
  tar -xzf /tmp/graalvm.tar.gz -C "\$SDKMAN_DIR/candidates/java/"
  rm /tmp/graalvm.tar.gz
else
  echo "Installing GraalVM via sdkman (first time)..."
  curl -s "https://get.sdkman.io" | bash
  source "\$SDKMAN_DIR/bin/sdkman-init.sh"
  sdk install java ${GRAALVM_VERSION}
  echo "Saving GraalVM to S3 cache..."
  tar -czf /tmp/graalvm.tar.gz -C "\$SDKMAN_DIR/candidates/java/" "${GRAALVM_VERSION}"
  aws s3 cp /tmp/graalvm.tar.gz "\$GRAALVM_CACHE" --region "${REGION}"
  rm /tmp/graalvm.tar.gz
fi

export JAVA_HOME="\$SDKMAN_DIR/candidates/java/${GRAALVM_VERSION}"
export PATH="\$JAVA_HOME/bin:\$PATH"
java -version

echo "=== Cloning restheart (${BRANCH}) ==="
git clone --depth 1 --branch ${BRANCH} https://github.com/SoftInstigate/restheart.git /opt/restheart
cd /opt/restheart

echo "=== Restoring Maven cache from S3 ==="
aws s3 sync "s3://${S3_BUCKET%%/*}/m2-cache/" /root/.m2/repository/ \
  --region "${REGION}" --quiet || true

echo "=== Starting native build ==="
NATIVE_PROFILES="-Pnative"
[[ "${WITH_TEST_PLUGINS}" == "true" ]] && NATIVE_PROFILES="-Pnative,test-native" && echo "(including test-plugins)"
./mvnw clean package \${NATIVE_PROFILES} -DskipTests

echo "=== Uploading binary ==="
aws s3 cp core/target/restheart "s3://${S3_BUCKET}/restheart-native-linux-amd64" --region "${REGION}"

echo "=== Saving Maven cache to S3 ==="
aws s3 sync /root/.m2/repository/ "s3://${S3_BUCKET%%/*}/m2-cache/" \
  --region "${REGION}" || true

echo "=== BUILD OK ==="
SCRIPT
)

# ── 4. launch spot instance ───────────────────────────────────────────────────
KEY_ARG=""
[[ -n "${KEY_NAME}" ]] && KEY_ARG="--key-name ${KEY_NAME}"

log "Launching spot instance ${INSTANCE_TYPE}..."
RUN_OUTPUT=$(aws ec2 run-instances \
  --image-id "${AMI_ID}" \
  --instance-type "${INSTANCE_TYPE}" \
  ${KEY_ARG} \
  --iam-instance-profile "Name=${IAM_PROFILE}" \
  --instance-market-options '{"MarketType":"spot"}' \
  --user-data "${USER_DATA}" \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=restheart-native-build}]" \
  --region "${REGION}" \
  --output json)

INSTANCE_ID=$(echo "${RUN_OUTPUT}" | python3 -c "import sys,json; print(json.load(sys.stdin)['Instances'][0]['InstanceId'])")
LAUNCH_TIME=$(echo "${RUN_OUTPUT}" | python3 -c "import sys,json; print(json.load(sys.stdin)['Instances'][0]['LaunchTime'])")
ok "Instance: ${INSTANCE_ID}  (launched: ${LAUNCH_TIME})"
log "Build log → s3://${BUILD_LOG_KEY}"

# ── 5. poll until terminated ───────────────────────────────────────────────────
log "Waiting for build to complete..."
while true; do
  STATE=$(aws ec2 describe-instances \
    --instance-ids "${INSTANCE_ID}" \
    --query 'Reservations[0].Instances[0].State.Name' \
    --output text --region "${REGION}")
  [[ "${STATE}" == "terminated" ]] && break
  log "  ${STATE} - build running..."
  sleep "${POLL_INTERVAL}"
done
ok "Instance terminated."

# ── 6. download and print build log ────────────────────────────────────────────
echo ""
log "Downloading build log..."
if aws s3 cp "s3://${BUILD_LOG_KEY}" /tmp/restheart-build.log --region "${REGION}" 2>/dev/null; then
  echo ""
  echo "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━ BUILD LOG ━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
  cat /tmp/restheart-build.log
  echo "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
else
  warn "Build log not found on S3."
fi

# ── 6. cost report ────────────────────────────────────────────────────────────
log "Calculating cost..."

AZ=$(aws ec2 describe-instances \
  --instance-ids "${INSTANCE_ID}" \
  --query 'Reservations[0].Instances[0].Placement.AvailabilityZone' \
  --output text --region "${REGION}")

SPOT_PRICE=$(aws ec2 describe-spot-price-history \
  --instance-types "${INSTANCE_TYPE}" \
  --product-descriptions "Linux/UNIX" \
  --availability-zone "${AZ}" \
  --start-time "${LAUNCH_TIME}" \
  --max-results 1 \
  --query 'SpotPriceHistory[0].SpotPrice' \
  --output text --region "${REGION}")

START_S=$(python3 -c "
from datetime import datetime
try:
    print(int(datetime.fromisoformat('${LAUNCH_TIME}'.replace('Z','+00:00')).timestamp()))
except Exception:
    print(0)
" 2>/dev/null || echo 0)
STOP_S=$(date -u '+%s')
DURATION_S=$(( STOP_S - START_S ))
DURATION_S=$(( DURATION_S < 60 ? 60 : DURATION_S ))
DURATION_MIN=$(( DURATION_S / 60 ))

COST=$(python3 -c "
try:
    price = float('${SPOT_PRICE}')
    print(f'{price * ${DURATION_S} / 3600:.4f}')
except Exception:
    print('N/A')
" 2>/dev/null || echo 'N/A')

echo ""
echo "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
echo "${BOLD}  RESTHeart native build — summary${RESET}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
printf "  %-22s %s\n"    "Instance:"    "${INSTANCE_ID}"
printf "  %-22s %s\n"    "Type:"        "${INSTANCE_TYPE}"
printf "  %-22s %s\n"    "AZ:"          "${AZ}"
printf "  %-22s %s\n"    "Duration:"    "${DURATION_MIN} min (${DURATION_S}s)"
printf "  %-22s \$%s/h\n" "Spot price:"  "${SPOT_PRICE}"
printf "  %-22s ${GREEN}${BOLD}\$%s${RESET}\n" "Total cost:" "${COST}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── 8. download binary if it exists on S3 ───────────────────────────────────
if aws s3 ls "s3://${S3_BUCKET}/restheart-native-linux-amd64" --region "${REGION}" &>/dev/null; then
  log "Downloading binary..."
  aws s3 cp "s3://${S3_BUCKET}/restheart-native-linux-amd64" ./restheart --region "${REGION}"
  chmod +x ./restheart
  ok "Binary ready: ./restheart"
else
  warn "Binary not found on S3 — build likely failed. Check the log above."
fi

log "To view the build log again:"
echo "  aws s3 cp s3://${BUILD_LOG_KEY} /tmp/restheart-build.log --region ${REGION} && cat /tmp/restheart-build.log"
