#!/usr/bin/env bash
# unifeed 서버 접속 (macOS / Linux)
#
#   bash connect-mac.sh          → 바로 접속 (비밀번호를 물어본다)
#   bash connect-mac.sh setup    → ~/.ssh/config 에 'unifeed' 를 등록만 한다. 그다음부터는 `ssh unifeed` 한 줄
#
# 서버: example.com  포트 47330  계정 ubuntu
# ⚠️ 한국 IP 에서만 접속된다 (해외 VPN·해외 로밍이면 막힌다).
# ⚠️ 비밀번호를 여러 번 틀리면 그 IP 가 잠시 차단된다(fail2ban). 몇 분 뒤 다시 시도.
# 웹: https://example.com  ← 서버 안에서 80 포트로 띄운 앱이 여기로 보인다.
set -euo pipefail

HOST_ALIAS="unifeed"
HOST="example.com"
PORT="47330"
USER_NAME="ubuntu"

command -v ssh >/dev/null || { echo "ssh 가 없습니다. macOS 는 기본으로 있어요 — 터미널을 다시 열어 보세요."; exit 1; }

setup() {
  mkdir -p "$HOME/.ssh" && chmod 700 "$HOME/.ssh"
  touch "$HOME/.ssh/config" && chmod 600 "$HOME/.ssh/config"
  if grep -qE "^Host[[:space:]]+${HOST_ALIAS}([[:space:]]|$)" "$HOME/.ssh/config"; then
    echo "이미 등록돼 있어요: ssh ${HOST_ALIAS}"
  else
    {
      echo ""
      echo "Host ${HOST_ALIAS}"
      echo "    HostName ${HOST}"
      echo "    Port ${PORT}"
      echo "    User ${USER_NAME}"
      echo "    ServerAliveInterval 30"
      echo "    ServerAliveCountMax 4"
    } >> "$HOME/.ssh/config"
    echo "등록했어요. 이제 터미널에서: ssh ${HOST_ALIAS}"
  fi
}

case "${1:-}" in
  setup) setup ;;
  "")    exec ssh -p "$PORT" -o ServerAliveInterval=30 -o ServerAliveCountMax=4 "${USER_NAME}@${HOST}" ;;
  *)     echo "사용법: bash connect-mac.sh [setup]"; exit 2 ;;
esac
