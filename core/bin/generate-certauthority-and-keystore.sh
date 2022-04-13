#!/usr/bin/env bash
# credits to:
# - Lorenzo Fontana
#    https://gist.github.com/fntlnz/cf14feb5a46b2eda428e000157447309
# - Maciej
#    https://betterdev.blog/minimal-safe-bash-script-template/

set -Eeuo pipefail
trap cleanup SIGINT SIGTERM ERR EXIT

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)

usage() {
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[0]}") [-h] [-v] [--no-color] -d mydomain.io -a /tmp -p mysecret

Creates a Certificate Authority, issues a certificate and import it a the java keystore to be used by RESTHeart
The Certificate Authority root certificate must be imported in OS or browsers.

Available options:

-h, --help      Print this help and exit
-v, --verbose   Print script debug info
-d, --domain    The domain of the certificate
-i, --ip        IP address to add to Subject Alternative Name extension (optional)
-a, --archive   The path where the generated files will be stored
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
  [[ -z "${password-}" ]] && die "Missing required parameter: password\nGet help with: $0 -h"
  [[ -z "${archive-}" ]] && die "Missing required parameter: archive\nGet help with: $0 -h"
  [[ -z "${domain-}" ]] && die "Missing required parameter: domain\nGet help with: $0 -h"

  return 0
}

parse_params "$@"
setup_colors

# script logic here

######################
# Generate the Certificate Authority
######################

# Generate private key
msg "${GREEN}Generate the Certificate Authority private key${NOFORMAT}"
[ -f "${archive}"/${domain}.key ] && msg "${BLUE}skipped, CA private key exists${NOFORMAT}"
[ ! -f "${archive}"/${domain}.key ] && openssl genrsa -out "${archive}"/devCA.key 4096
# Generate root certificate
msg "${GREEN}Create and self sign the Root Certificate${NOFORMAT}"
[ -f "${archive}"/${domain}.key ] && msg "${BLUE}skipped, root certificate exists${NOFORMAT}"
openssl req -x509 -new -nodes -key "${archive}"/devCA.key -subj "/CN=devCA" -sha256 -days 1024 -out "${archive}"/devCA.pem
[ ! -f "${archive}"/${domain}.key ] && msg "${GREEN}Certificate Authority certificate ${RED}(TO BE IMPORTED IN BROWSER)${GREEN}: "${archive}"/devCA.pem${NOFORMAT}"

######################
# Create CA-signed certs
######################

# Generate a private key
openssl genrsa -out "${archive}"/${domain}.key 2048

# Create a certificate-signing request
msg "${GREEN}Create a certificate-signing request${NOFORMAT}"
openssl req -new -sha256 -key "${archive}"/${domain}.key -subj "/CN=${domain}" -out "${archive}"/${domain}.csr
#openssl req -new -key "${archive}"/${domain}.key -passout pass:"${password}" -out ${archive}/${domain}.csr
# Create a config file for the extensions
>"${archive}"/${domain}.ext cat <<-EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
subjectAltName = @alt_names
[alt_names]
DNS.1 = ${domain}   # Be sure to include the domain name here because Common Name is not so commonly honoured by itself
EOF

# Add the IP address if specified
[[ ! -z "${ip-}" ]] && echo -e "IP.1 = ${ip}\n" >> "${archive}"/${domain}.ext

msg "${GREEN}Generate the certificate${NOFORMAT}"
openssl x509 -extfile "${archive}"/${domain}.ext -req -in "${archive}"/${domain}.csr -CA "${archive}"/devCA.pem -CAkey "${archive}"/devCA.key -CAcreateserial -out "${archive}"/${domain}.pem -days 365 -sha256

KEYSTORE=${archive}/${domain}

msg "${GREEN}Convert certificate to PKCS 12 archive${NOFORMAT}"
openssl pkcs12 -export \
	-in "${archive}"/${domain}.pem \
	-inkey "${archive}"/${domain}.key \
	-out "${KEYSTORE}".p12 \
	-name "${domain}" \
	-CAfile "${archive}"/devCA.pem \
    -caname "devCA" \
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

#keytool -importcert -keystore ${KEYSTORE} -trustcacerts -storepass ${password} -noprompt  -alias "devCA" -file "${archive}/devCA.pem"

msg "${GREEN}Done: keystore ${KEYSTORE}.jks, CA root certificate: "${archive}"/devCA.pem${NOFORMAT}"