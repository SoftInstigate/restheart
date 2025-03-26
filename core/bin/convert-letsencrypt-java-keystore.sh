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
Usage: $(basename "${BASH_SOURCE[0]}") [-h] [-v] [--no-color] -d mydomain.org -c mydomain.org.crt cert -k mydomain.org.pem -i mydomain.org.org.issuer.crt -p mysecret

Generate the java keystore from letsencrypt certificate

Available options:

-h, --help      Print this help and exit
-v, --verbose   Print script debug info
-d, --domain    The domain of the certificate
-c, --cert      The certificate file name
-k, --key       The private key file name
-i, --issuer    The issuer file name
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
  msg ""
  usage
  exit "$code"
}

parse_params() {
  # default values of variables set from params
  password=''
  domain=''

  while :; do
    case "${1-}" in
    -h | --help) usage ;;
    -v | --verbose) set -x ;;
    --no-color) NO_COLOR=1 ;;
    -p | --password) # password named parameter
      password="${2-}"
      shift
      ;;
    -d | --domain) # domain named parameter
      domain="${2-}"
      shift
      ;;
    -c | --cert) # domain named parameter
      CERT_FILE="${2-}"
      shift
      ;;
    -k | --key) # domain named parameter
      PRIVATE_KEY_FILE="${2-}"
      shift
      ;;
    -i | --issuer) # domain named parameter
      CA_FILE="${2-}"
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
  [[ -z "${domain-}" ]] && die "Missing required parameter: domain"
  [[ -z "${CERT_FILE-}" ]] && die "Missing required parameter: cert"
  [[ -z "${PRIVATE_KEY_FILE-}" ]] && die "Missing required parameter: key"
  [[ -z "${CA_FILE-}" ]] && die "Missing required parameter: issuer"

  return 0
}

parse_params "$@"
setup_colors

# script logic here

outdir=$(dirname "$CERT_FILE")

KEYSTORE=${outdir}/${domain}

msg "${GREEN}Convert Let's Encrypt certificates to PKCS 12 archive${NOFORMAT}"
openssl pkcs12 -export \
	-in "${CERT_FILE}" \
	-inkey "${PRIVATE_KEY_FILE}" \
	-out "${KEYSTORE}".p12 \
	-name "${domain}" \
	-CAfile "${CA_FILE}" \
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

msg "${GREEN}Add the necessary Let's Encrypt intermediate certs.${NOFORMAT}"
# Add the necessary Let's Encrypt intermediate certs
# (see https://gist.github.com/richmilne/5a5cb4be0ec8233a6c50ba40229d8278)
certs=(
    lets-encrypt-r3
)

KEYSTORE="${KEYSTORE}".jks
for ALIAS in ${!certs[@]}
do
    FNAME="${certs[$ALIAS]}".pem
    # insecure to support old versions of openssl
    curl --insecure https://letsencrypt.org/certs/${FNAME} > ${outdir}/${FNAME}
    keytool -delete -alias ${certs[$ALIAS]} -keystore ${KEYSTORE} -storepass ${password} > /dev/null || true
    keytool -importcert -keystore ${KEYSTORE} -trustcacerts -storepass ${password} -noprompt  -alias ${certs[$ALIAS]} -file "${outdir}/${FNAME}"
done
rm -v "${outdir}"/*.pem.txt 2> /dev/null

msg "${GREEN}Done: ${outdir}/${KEYSTORE}${NOFORMAT}"