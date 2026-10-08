#!/usr/bin/env bash
# WSL2 eth0 의 IPv4 주소를 HOST_IP=... 형태로 찍는다.
#
#   ./scripts/host-ip.sh            # 확인용
#   ./scripts/host-ip.sh >> .env    # .env 에 추가
#
# 이 값은 WSL 을 재시작하면 바뀐다. 바뀐 채로 두면 Sentinel 이 죽은 주소를
# 감시하게 되어 "master 는 살아 있는데 sdown" 이 뜬다.
# 기존 load-test 도 같은 값을 BASE_URL 에 박아 쓰고 있다 (load-test/run.sh:5).
set -eu
ip=$(ip -4 -o addr show eth0 2>/dev/null | awk '{print $4}' | cut -d/ -f1 | head -1)
if [ -z "${ip:-}" ]; then
  echo "eth0 주소를 못 찾았다. WSL2 가 아니거나 인터페이스 이름이 다르다" >&2
  exit 1
fi
echo "HOST_IP=${ip}"
