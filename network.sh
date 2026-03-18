#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export COMPOSE_PROJECT_NAME="chainsettle"
export FABRIC_CFG_PATH="${ROOT_DIR}/fabric-config"

CHAINCODE_NAME="${CHAINSETTLE_CHAINCODE_NAME:-token-settlement}"
CHANNEL_NAME="${CHAINSETTLE_CHANNEL_NAME:-settlement-channel}"
CHAINCODE_VERSION="${CHAINSETTLE_CHAINCODE_VERSION:-1.0}"
CHAINCODE_SEQUENCE="${CHAINSETTLE_CHAINCODE_SEQUENCE:-1}"
FABRIC_VERSION="${CHAINSETTLE_FABRIC_VERSION:-2.5.15}"
CA_VERSION="${CHAINSETTLE_CA_VERSION:-1.5.12}"

FABRIC_CACHE="${ROOT_DIR}/.fabric"
FABRIC_BIN_DIR="${FABRIC_CACHE}/bin"
FABRIC_TARBALL_DIR="${FABRIC_CACHE}/downloads"
CHAINCODE_LABEL="${CHAINCODE_NAME}_${CHAINCODE_VERSION}"
ORDERER_CA="${ROOT_DIR}/organizations/ordererOrganizations/chainsettle.com/orderers/orderer.chainsettle.com/msp/tlscacerts/tlsca.chainsettle.com-cert.pem"

OS_NAME="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH_NAME="$(uname -m)"

if [[ "${ARCH_NAME}" == "x86_64" ]]; then
  ARCH_NAME="amd64"
elif [[ "${ARCH_NAME}" == "arm64" || "${ARCH_NAME}" == "aarch64" ]]; then
  ARCH_NAME="arm64"
fi

compose_cmd() {
  if command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    docker compose "$@"
  fi
}

require_binary() {
  local binary="$1"
  if ! command -v "${binary}" >/dev/null 2>&1; then
    echo "Missing required tool: ${binary}" >&2
    exit 1
  fi
}

download_file() {
  local url="$1"
  local destination="$2"
  mkdir -p "$(dirname "${destination}")"
  if [[ -f "${destination}" ]]; then
    return
  fi
  echo "Downloading ${url}"
  curl -fsSL "${url}" -o "${destination}"
}

download_fabric_tools() {
  if [[ -x "${FABRIC_BIN_DIR}/cryptogen" && -x "${FABRIC_BIN_DIR}/configtxgen" && -x "${FABRIC_BIN_DIR}/peer" ]]; then
    return
  fi

  require_binary curl
  require_binary tar

  mkdir -p "${FABRIC_BIN_DIR}" "${FABRIC_TARBALL_DIR}"

  local fabric_archive="${FABRIC_TARBALL_DIR}/hyperledger-fabric-${OS_NAME}-${ARCH_NAME}-${FABRIC_VERSION}.tar.gz"
  local ca_archive="${FABRIC_TARBALL_DIR}/hyperledger-fabric-ca-${OS_NAME}-${ARCH_NAME}-${CA_VERSION}.tar.gz"

  download_file "https://github.com/hyperledger/fabric/releases/download/v${FABRIC_VERSION}/hyperledger-fabric-${OS_NAME}-${ARCH_NAME}-${FABRIC_VERSION}.tar.gz" "${fabric_archive}"
  download_file "https://github.com/hyperledger/fabric-ca/releases/download/v${CA_VERSION}/hyperledger-fabric-ca-${OS_NAME}-${ARCH_NAME}-${CA_VERSION}.tar.gz" "${ca_archive}"

  tar -xzf "${fabric_archive}" -C "${FABRIC_CACHE}"
  tar -xzf "${ca_archive}" -C "${FABRIC_CACHE}"
}

export_path() {
  export PATH="${FABRIC_BIN_DIR}:${PATH}"
}

generate_connection_profiles() {
  cat > "${ROOT_DIR}/fabric-config/connection-bankalpha.json" <<EOF
{
  "orgName": "BankAlpha",
  "mspId": "BankAlphaMSP",
  "peerEndpoint": "peer0.bankalpha.chainsettle.com:7051",
  "peerHostAlias": "peer0.bankalpha.chainsettle.com",
  "tlsCertPath": "organizations/peerOrganizations/bankalpha.chainsettle.com/peers/peer0.bankalpha.chainsettle.com/tls/ca.crt",
  "identityCertPath": "organizations/peerOrganizations/bankalpha.chainsettle.com/users/User1@bankalpha.chainsettle.com/msp/signcerts/cert.pem",
  "privateKeyPath": "organizations/peerOrganizations/bankalpha.chainsettle.com/users/User1@bankalpha.chainsettle.com/msp/keystore/priv_sk"
}
EOF
  cat > "${ROOT_DIR}/fabric-config/connection-bankbeta.json" <<EOF
{
  "orgName": "BankBeta",
  "mspId": "BankBetaMSP",
  "peerEndpoint": "peer0.bankbeta.chainsettle.com:9051",
  "peerHostAlias": "peer0.bankbeta.chainsettle.com",
  "tlsCertPath": "organizations/peerOrganizations/bankbeta.chainsettle.com/peers/peer0.bankbeta.chainsettle.com/tls/ca.crt",
  "identityCertPath": "organizations/peerOrganizations/bankbeta.chainsettle.com/users/User1@bankbeta.chainsettle.com/msp/signcerts/cert.pem",
  "privateKeyPath": "organizations/peerOrganizations/bankbeta.chainsettle.com/users/User1@bankbeta.chainsettle.com/msp/keystore/priv_sk"
}
EOF
  cat > "${ROOT_DIR}/fabric-config/connection-clearinghouse.json" <<EOF
{
  "orgName": "ClearingHouse",
  "mspId": "ClearingHouseMSP",
  "peerEndpoint": "peer0.clearinghouse.chainsettle.com:11051",
  "peerHostAlias": "peer0.clearinghouse.chainsettle.com",
  "tlsCertPath": "organizations/peerOrganizations/clearinghouse.chainsettle.com/peers/peer0.clearinghouse.chainsettle.com/tls/ca.crt",
  "identityCertPath": "organizations/peerOrganizations/clearinghouse.chainsettle.com/users/User1@clearinghouse.chainsettle.com/msp/signcerts/cert.pem",
  "privateKeyPath": "organizations/peerOrganizations/clearinghouse.chainsettle.com/users/User1@clearinghouse.chainsettle.com/msp/keystore/priv_sk"
}
EOF
}

clean_artifacts() {
  rm -rf "${ROOT_DIR}/organizations" "${ROOT_DIR}/channel-artifacts" "${ROOT_DIR}/system-genesis-block"
  mkdir -p "${ROOT_DIR}/organizations/fabric-ca/bankalpha" \
           "${ROOT_DIR}/organizations/fabric-ca/bankbeta" \
           "${ROOT_DIR}/organizations/fabric-ca/clearinghouse" \
           "${ROOT_DIR}/channel-artifacts" \
           "${ROOT_DIR}/system-genesis-block"
}

generate_artifacts() {
  download_fabric_tools
  export_path
  clean_artifacts

  cryptogen generate --config="${ROOT_DIR}/fabric-config/crypto-config.yaml" --output="${ROOT_DIR}/organizations"

  configtxgen -profile ChainSettleGenesis \
    -channelID system-channel \
    -outputBlock "${ROOT_DIR}/system-genesis-block/genesis.block"

  configtxgen -profile ChainSettleChannel \
    -outputCreateChannelTx "${ROOT_DIR}/channel-artifacts/${CHANNEL_NAME}.tx" \
    -channelID "${CHANNEL_NAME}"

  configtxgen -profile ChainSettleChannel \
    -outputAnchorPeersUpdate "${ROOT_DIR}/channel-artifacts/BankAlphaMSPanchors.tx" \
    -channelID "${CHANNEL_NAME}" \
    -asOrg BankAlphaMSP

  configtxgen -profile ChainSettleChannel \
    -outputAnchorPeersUpdate "${ROOT_DIR}/channel-artifacts/BankBetaMSPanchors.tx" \
    -channelID "${CHANNEL_NAME}" \
    -asOrg BankBetaMSP

  configtxgen -profile ChainSettleChannel \
    -outputAnchorPeersUpdate "${ROOT_DIR}/channel-artifacts/ClearingHouseMSPanchors.tx" \
    -channelID "${CHANNEL_NAME}" \
    -asOrg ClearingHouseMSP

  generate_connection_profiles
}

create_channel() {
  set_globals BankAlpha
  peer channel create \
    -o localhost:7050 \
    --ordererTLSHostnameOverride orderer.chainsettle.com \
    -c "${CHANNEL_NAME}" \
    -f "${ROOT_DIR}/channel-artifacts/${CHANNEL_NAME}.tx" \
    --outputBlock "${ROOT_DIR}/channel-artifacts/${CHANNEL_NAME}.block" \
    --tls \
    --cafile "${ORDERER_CA}"
}

join_channel() {
  for org in BankAlpha BankBeta ClearingHouse; do
    set_globals "${org}"
    peer channel join -b "${ROOT_DIR}/channel-artifacts/${CHANNEL_NAME}.block"
  done
}

update_anchor_peers() {
  set_globals BankAlpha
  peer channel update -o localhost:7050 --ordererTLSHostnameOverride orderer.chainsettle.com \
    -c "${CHANNEL_NAME}" -f "${ROOT_DIR}/channel-artifacts/BankAlphaMSPanchors.tx" \
    --tls --cafile "${ORDERER_CA}"

  set_globals BankBeta
  peer channel update -o localhost:7050 --ordererTLSHostnameOverride orderer.chainsettle.com \
    -c "${CHANNEL_NAME}" -f "${ROOT_DIR}/channel-artifacts/BankBetaMSPanchors.tx" \
    --tls --cafile "${ORDERER_CA}"

  set_globals ClearingHouse
  peer channel update -o localhost:7050 --ordererTLSHostnameOverride orderer.chainsettle.com \
    -c "${CHANNEL_NAME}" -f "${ROOT_DIR}/channel-artifacts/ClearingHouseMSPanchors.tx" \
    --tls --cafile "${ORDERER_CA}"
}

set_globals() {
  local org="$1"
  export CORE_PEER_TLS_ENABLED=true
  export CORE_PEER_TLS_ROOTCERT_FILE=""
  export CORE_PEER_MSPCONFIGPATH=""
  export CORE_PEER_ADDRESS=""
  export CORE_PEER_LOCALMSPID=""

  case "${org}" in
    BankAlpha)
      export CORE_PEER_LOCALMSPID="BankAlphaMSP"
      export CORE_PEER_TLS_ROOTCERT_FILE="${ROOT_DIR}/organizations/peerOrganizations/bankalpha.chainsettle.com/peers/peer0.bankalpha.chainsettle.com/tls/ca.crt"
      export CORE_PEER_MSPCONFIGPATH="${ROOT_DIR}/organizations/peerOrganizations/bankalpha.chainsettle.com/users/Admin@bankalpha.chainsettle.com/msp"
      export CORE_PEER_ADDRESS="localhost:7051"
      ;;
    BankBeta)
      export CORE_PEER_LOCALMSPID="BankBetaMSP"
      export CORE_PEER_TLS_ROOTCERT_FILE="${ROOT_DIR}/organizations/peerOrganizations/bankbeta.chainsettle.com/peers/peer0.bankbeta.chainsettle.com/tls/ca.crt"
      export CORE_PEER_MSPCONFIGPATH="${ROOT_DIR}/organizations/peerOrganizations/bankbeta.chainsettle.com/users/Admin@bankbeta.chainsettle.com/msp"
      export CORE_PEER_ADDRESS="localhost:8051"
      ;;
    ClearingHouse)
      export CORE_PEER_LOCALMSPID="ClearingHouseMSP"
      export CORE_PEER_TLS_ROOTCERT_FILE="${ROOT_DIR}/organizations/peerOrganizations/clearinghouse.chainsettle.com/peers/peer0.clearinghouse.chainsettle.com/tls/ca.crt"
      export CORE_PEER_MSPCONFIGPATH="${ROOT_DIR}/organizations/peerOrganizations/clearinghouse.chainsettle.com/users/Admin@clearinghouse.chainsettle.com/msp"
      export CORE_PEER_ADDRESS="localhost:9051"
      ;;
    *)
      echo "Unknown org: ${org}" >&2
      exit 1
      ;;
  esac
}

package_chaincode() {
  export_path
  rm -f "${ROOT_DIR}/${CHAINCODE_NAME}.tar.gz"
  set_globals BankAlpha
  peer lifecycle chaincode package "${ROOT_DIR}/${CHAINCODE_NAME}.tar.gz" \
    --path "${ROOT_DIR}/chaincode" \
    --lang java \
    --label "${CHAINCODE_LABEL}"
}

install_chaincode() {
  for org in BankAlpha BankBeta ClearingHouse; do
    set_globals "${org}"
    peer lifecycle chaincode install "${ROOT_DIR}/${CHAINCODE_NAME}.tar.gz"
  done
}

query_installed() {
  set_globals BankAlpha
  peer lifecycle chaincode queryinstalled > "${ROOT_DIR}/channel-artifacts/queryinstalled.log"
  PACKAGE_ID="$(sed -n "s/^Package ID: \\(.*\\), Label: ${CHAINCODE_LABEL}$/\\1/p" "${ROOT_DIR}/channel-artifacts/queryinstalled.log" | head -n 1)"
  if [[ -z "${PACKAGE_ID:-}" ]]; then
    echo "Unable to determine package ID for ${CHAINCODE_LABEL}" >&2
    exit 1
  fi
}

approve_for_org() {
  local org="$1"
  set_globals "${org}"
  peer lifecycle chaincode approveformyorg \
    -o localhost:7050 \
    --ordererTLSHostnameOverride orderer.chainsettle.com \
    --channelID "${CHANNEL_NAME}" \
    --name "${CHAINCODE_NAME}" \
    --version "${CHAINCODE_VERSION}" \
    --package-id "${PACKAGE_ID}" \
    --sequence "${CHAINCODE_SEQUENCE}" \
    --tls \
    --cafile "${ORDERER_CA}" \
    --signature-policy "OutOf(2, 'BankAlphaMSP.peer', 'BankBetaMSP.peer', 'ClearingHouseMSP.peer')"
}

commit_chaincode() {
  set_globals BankAlpha
  peer lifecycle chaincode commit \
    -o localhost:7050 \
    --ordererTLSHostnameOverride orderer.chainsettle.com \
    --channelID "${CHANNEL_NAME}" \
    --name "${CHAINCODE_NAME}" \
    --version "${CHAINCODE_VERSION}" \
    --sequence "${CHAINCODE_SEQUENCE}" \
    --tls \
    --cafile "${ORDERER_CA}" \
    --signature-policy "OutOf(2, 'BankAlphaMSP.peer', 'BankBetaMSP.peer', 'ClearingHouseMSP.peer')" \
    --peerAddresses localhost:7051 \
    --tlsRootCertFiles "${ROOT_DIR}/organizations/peerOrganizations/bankalpha.chainsettle.com/peers/peer0.bankalpha.chainsettle.com/tls/ca.crt" \
    --peerAddresses localhost:8051 \
    --tlsRootCertFiles "${ROOT_DIR}/organizations/peerOrganizations/bankbeta.chainsettle.com/peers/peer0.bankbeta.chainsettle.com/tls/ca.crt" \
    --peerAddresses localhost:9051 \
    --tlsRootCertFiles "${ROOT_DIR}/organizations/peerOrganizations/clearinghouse.chainsettle.com/peers/peer0.clearinghouse.chainsettle.com/tls/ca.crt"
}

verify_deployment() {
  set_globals BankAlpha
  peer lifecycle chaincode querycommitted --channelID "${CHANNEL_NAME}" --name "${CHAINCODE_NAME}"
}

start_network() {
  generate_artifacts
  compose_cmd -f "${ROOT_DIR}/docker-compose-fabric.yaml" up -d
  sleep 8
  create_channel
  join_channel
  update_anchor_peers
  deploy_chaincode
}

deploy_chaincode() {
  package_chaincode
  install_chaincode
  query_installed
  approve_for_org BankAlpha
  approve_for_org BankBeta
  approve_for_org ClearingHouse
  commit_chaincode
  verify_deployment
}

stop_network() {
  compose_cmd -f "${ROOT_DIR}/docker-compose-fabric.yaml" down -v --remove-orphans
  rm -f "${ROOT_DIR}/${CHAINCODE_NAME}.tar.gz"
}

restart_network() {
  stop_network
  start_network
}

usage() {
  cat <<EOF
Usage: ./network.sh <command>

Commands:
  up         Generate artifacts, start Fabric, create channel, and deploy chaincode
  down       Stop Fabric containers and clean packaged chaincode artifact
  restart    Rebuild artifacts and restart the Fabric network
  generate   Generate crypto material, channel artifacts, and connection profiles
  deployCC   Package, install, approve, commit, and verify chaincode
EOF
}

main() {
  case "${1:-}" in
    up)
      start_network
      ;;
    down)
      stop_network
      ;;
    restart)
      restart_network
      ;;
    generate)
      generate_artifacts
      ;;
    deployCC)
      deploy_chaincode
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "${1:-}"
