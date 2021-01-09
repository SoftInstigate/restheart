#!/usr/bin/env bash
# credits to:
# - richmilne https://github.com/richmilne
#    A codificiation of the steps outlined at
#    https://ordina-jworks.github.io/security/2019/08/14/Using-Lets-Encrypt-Certificates-In-Java.html
# - Maciej
#    https://betterdev.blog/minimal-safe-bash-script-template/

set -Eeuo pipefail
trap cleanup SIGINT SIGTERM ERR EXIT

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)

usage() {
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[0]}") [-h] [-v] [--no-color] -d mydomain.io -a /tmp/letsencrypt/archive -p mysecret

Generate a self signed certificate and import it in a java keystore

Available options:

-h, --help      Print this help and exit
-v, --verbose   Print script debug info
-d, --domain    The domain of the certificate
-i, --ip        IP address to add to Subject Alternative Name extension (optional)
-a, --archive   The path of the unzipped certificate archive directory containing cert.pem, chain.pem and privkey.pem
-p, --password  The keystore and certificate password (will be equal)
--no-color      Don't use colors
EOF
  exit
}

cleanup() {
  trap - SIGINT SIGTERM ERR EXIT
  # script cleanup here
}

setup_colors() {
  if [[ -t 2 ]] && [[ -z "${NO_COLOR-}" ]] && [[ "${TERM-}" != "dumb" ]]; then
    NOFORMAT='\033[0m' RED='\033[0;31m' GREEN='\033[0;32m' ORANGE='\033[0;33m' BLUE='\033[0;34m' PURPLE='\033[0;35m' CYAN='\033[0;36m' YELLOW='\033[1;33m'
  else
    NOFORMAT='' RED='' GREEN='' ORANGE='' BLUE='' PURPLE='' CYAN='' YELLOW=''
  fi
}

msg() {
  echo >&2 -e "${1-}"
}

die() {
  local msg=$1
  local code=${2-1} # default exit status 1
  msg "$msg"
  exit "$code"
}

parse_params() {
  # default values of variables set from params
  password=''
  archive=''
  domain=''
  ip=''

  while :; do
    case "${1-}" in
    -h | --help) usage ;;
    -v | --verbose) set -x ;;
    --no-color) NO_COLOR=1 ;;
    -a | --archive) # archive named parameter
      archive="${2-}"
      shift
      ;;
    -p | --password) # password named parameter
      password="${2-}"
      shift
      ;;
    -d | --domain) # domain named parameter
      domain="${2-}"
      shift
      ;;
    -i | --ip) # ip named parameter
      ip="${2-}"
      shift
      ;;
    -?*) die "Unknown option: $1" ;;
    *) break ;;
    esac
    shift
  done

  args=("$@")

  # check required params and arguments
  [[ -z "${password-}" ]] && die "Missing required parameter: password"
  [[ -z "${archive-}" ]] && die "Missing required parameter: archive"
  [[ -z "${domain-}" ]] && die "Missing required parameter: domain"

  return 0
}

parse_params "$@"
setup_colors

# script logic here

msg "${GREEN}Generate the self signed certificate${NOFORMAT}"

sanconfig="[re] distinguished_name=req [san] subjectAltName=DNS:${domain},DNS:${domain}"
[[ -z "${ip-}" ]] && sanconfig=${sanconfig},IP:${ip}

openssl req -x509 -newkey rsa:4096 -sha256 -keyout "${archive}"/privkey.pem -out "${archive}"/cert.pem -days 365 -subj "/CN=${domain}" -nodes

KEYSTORE=${archive}/${domain}

msg "${GREEN}Convert Let's Encrypt certificates to PKCS 12 archive${NOFORMAT}"
openssl pkcs12 -export \
	-in "${archive}"/cert.pem \
	-inkey "${archive}"/privkey.pem \
	-out "${KEYSTORE}".p12 \
	-name "${domain}" \
	-CAfile "${archive}"/fullchain.pem \
	-caname "Let's Encrypt Authority X3" \
	-password pass:"${password}"

msg "${GREEN}Import certificates into a keystore file.${NOFORMAT}"
keytool -importkeystore \
	-srckeystore "${KEYSTORE}".p12 \
	-srcstoretype PKCS12 \
	-srcstorepass "${password}" \
	-destkeystore "${KEYSTORE}".jks \
	-deststoretype PKCS12 \
	-deststorepass "${password}" \
	-destkeypass "${password}" \
	-alias ${domain}

msg "${GREEN}Done: ${KEYSTORE}${NOFORMAT}"