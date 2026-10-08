#!/bin/sh
# redis 노드 기동. 템플릿을 치환해서 /data/redis.conf 를 만들고 그 파일로 띄운다.
#
# 왜 복사하나: Sentinel 이 페일오버할 때 이 노드에 CONFIG REWRITE 를 시킨다.
# 바인드 마운트한 템플릿을 직접 쓰면 호스트 파일이 고쳐진다 (redis/master.conf 주석).
#
# 왜 "없을 때만" 만드나: docker restart 로 컨테이너를 다시 띄울 때 이미 반영된
# 승격/강등 상태를 지우면 안 된다. "구 master 를 되살렸더니 다시 master 로
# 행세한다" 는 인위적인 상황이 만들어져 실험 5 가 무의미해진다.
# 처음부터 다시 하려면 scripts/reset.sh (docker compose down -v) 를 쓴다.
set -eu

: "${TEMPLATE:?TEMPLATE 이 필요하다 (master.conf | replica.conf)}"
: "${ANNOUNCE_IP:?ANNOUNCE_IP 가 필요하다}"
: "${ANNOUNCE_PORT:?ANNOUNCE_PORT 가 필요하다}"
MASTER_IP="${MASTER_IP:-}"
MASTER_PORT="${MASTER_PORT:-6379}"
MIN_REPLICAS_TO_WRITE="${MIN_REPLICAS_TO_WRITE:-0}"
MIN_REPLICAS_MAX_LAG="${MIN_REPLICAS_MAX_LAG:-10}"

CONF=/data/redis.conf
mkdir -p /data

if [ ! -f "$CONF" ] || [ "${FORCE_REGEN:-0}" = "1" ]; then
  sed -e "s|__ANNOUNCE_IP__|${ANNOUNCE_IP}|g" \
      -e "s|__ANNOUNCE_PORT__|${ANNOUNCE_PORT}|g" \
      -e "s|__MASTER_IP__|${MASTER_IP}|g" \
      -e "s|__MASTER_PORT__|${MASTER_PORT}|g" \
      -e "s|__MIN_REPLICAS_TO_WRITE__|${MIN_REPLICAS_TO_WRITE}|g" \
      -e "s|__MIN_REPLICAS_MAX_LAG__|${MIN_REPLICAS_MAX_LAG}|g" \
      "/templates/${TEMPLATE}" > "$CONF"
  echo "[entrypoint] ${CONF} 를 /templates/${TEMPLATE} 에서 새로 만들었다"
else
  echo "[entrypoint] ${CONF} 가 이미 있다. 그대로 쓴다 (재시작 시 승격 상태 유지)"
fi

exec redis-server "$CONF"
