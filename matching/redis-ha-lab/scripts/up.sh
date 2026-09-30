#!/usr/bin/env bash
# 실험 환경을 띄우고 정족수가 찰 때까지 기다린다.
#
#   ./scripts/up.sh              # T-A (기본)
#   NETPART=1 ./scripts/up.sh    # T-B (네트워크 파티션 실험용)
#
# .env 의 HOST_IP 가 지금 WSL 주소와 다르면 여기서 먼저 걸러 준다.
# 그냥 띄우면 5초 뒤 Sentinel 이 죽은 주소를 sdown 으로 잡고 페일오버를 시작해서
# "왜 아무것도 안 했는데 페일오버가 났나" 로 30분을 태우게 된다.
set -eu
. "$(dirname "$0")/lib.sh"

if [ ! -f "${LAB_DIR}/.env" ]; then
  echo ".env 가 없다. 먼저 만들어라:" >&2
  echo "  cp ${LAB_DIR}/.env.example ${LAB_DIR}/.env" >&2
  echo "  ${LAB_DIR}/scripts/host-ip.sh >> ${LAB_DIR}/.env" >&2
  exit 1
fi

if [ "${NETPART:-0}" != "1" ]; then
  want=$("${LAB_DIR}/scripts/host-ip.sh" | cut -d= -f2)
  have=$(grep -E '^HOST_IP=' "${LAB_DIR}/.env" | tail -1 | cut -d= -f2)
  if [ "$want" != "$have" ]; then
    echo ".env 의 HOST_IP(${have}) 가 지금 eth0 주소(${want}) 와 다르다." >&2
    echo "고치고 다시 실행해라. 안 고치면 Sentinel 이 죽은 주소를 감시한다." >&2
    exit 1
  fi
fi

dc up -d

echo "--- Sentinel 정족수가 찰 때까지 기다린다 ---"
for i in $(seq 1 60); do
  n=$(sent SENTINEL sentinels mymaster 2>/dev/null | grep -c '^name$' || true)
  # 자기 자신은 목록에 없다. 3대면 서로 2대씩 본다.
  if [ "${n:-0}" -ge 2 ]; then
    echo "Sentinel 상호 인식 완료 (자기 제외 ${n}대)"
    break
  fi
  sleep 1
done

echo "--- replica 가 붙을 때까지 기다린다 ---"
for i in $(seq 1 60); do
  r=$(sent SENTINEL replicas mymaster 2>/dev/null | grep -c '^name$' || true)
  if [ "${r:-0}" -ge 2 ]; then echo "replica ${r}대 인식 완료"; break; fi
  sleep 1
done

"${LAB_DIR}/scripts/status.sh"
