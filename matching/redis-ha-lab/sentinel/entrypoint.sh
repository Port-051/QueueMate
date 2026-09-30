#!/bin/sh
# Sentinel 기동. 템플릿을 치환해서 /data/sentinel.conf 를 만들고 그 파일로 띄운다.
#
# "없을 때만 만든다" 가 핵심이다. Sentinel 의 설정 파일은 상태 저장소이기도 해서
# (sentinel/sentinel.conf 주석) 재시작마다 템플릿으로 덮어쓰면 이렇게 깨진다:
#   1. myid 가 매번 새로 생긴다  -> 다른 Sentinel 이 "새 Sentinel 이 늘었다" 로 보고
#      known-sentinel 목록에 유령 항목이 쌓인다. 정족수 계산이 어긋난다.
#   2. sentinel monitor 가 최초 master 주소로 되돌아간다 -> 이미 페일오버가 끝나
#      다른 노드가 master 인데 죽은 노드를 감시하기 시작한다.
# 그래서 compose 는 /data 를 named volume 으로 잡고, 판을 새로 깔 때만
# scripts/reset.sh (docker compose down -v) 로 볼륨째 지운다.
set -eu

: "${ANNOUNCE_IP:?ANNOUNCE_IP 가 필요하다}"
: "${ANNOUNCE_PORT:?ANNOUNCE_PORT 가 필요하다}"
: "${MASTER_IP:?MASTER_IP 가 필요하다}"
MASTER_PORT="${MASTER_PORT:-6379}"
QUORUM="${QUORUM:-2}"
DOWN_AFTER="${DOWN_AFTER:-5000}"
FAILOVER_TIMEOUT="${FAILOVER_TIMEOUT:-60000}"
PARALLEL_SYNCS="${PARALLEL_SYNCS:-1}"

CONF=/data/sentinel.conf
mkdir -p /data

if [ ! -f "$CONF" ] || [ "${FORCE_REGEN:-0}" = "1" ]; then
  sed -e "s|__ANNOUNCE_IP__|${ANNOUNCE_IP}|g" \
      -e "s|__ANNOUNCE_PORT__|${ANNOUNCE_PORT}|g" \
      -e "s|__MASTER_IP__|${MASTER_IP}|g" \
      -e "s|__MASTER_PORT__|${MASTER_PORT}|g" \
      -e "s|__QUORUM__|${QUORUM}|g" \
      -e "s|__DOWN_AFTER__|${DOWN_AFTER}|g" \
      -e "s|__FAILOVER_TIMEOUT__|${FAILOVER_TIMEOUT}|g" \
      -e "s|__PARALLEL_SYNCS__|${PARALLEL_SYNCS}|g" \
      /templates/sentinel.conf > "$CONF"
  echo "[entrypoint] ${CONF} 를 새로 만들었다"
else
  echo "[entrypoint] ${CONF} 가 이미 있다. 그대로 쓴다 (myid / 감시 대상 유지)"
fi

# Sentinel 은 이 파일에 계속 써야 한다. 쓰기 권한이 없으면 기동은 되고
# 페일오버 시점에 조용히 실패한다.
exec redis-sentinel "$CONF"
