#!/usr/bin/env bash
# HTTP 201 은 받았는데 파티 배정까지는 못 간 요청을 센다.
#
#   ./scripts/assign-gap.sh f1757400000       # run-probe.sh 가 찍은 RUN_ID
#
# 왜 이게 따로 필요한가:
#   컨트롤러는 claim 만 하고 201 을 돌려준다 (MatchingController.java:46-55).
#   파티 배정은 @Async 로 뒤에서 돈다 (MatchTrigger.java:25). 그 안에서 터진 예외는
#   AsyncConfig.java:38-41 의 핸들러가 로그만 남기고 삼킨다.
#   즉 페일오버 중에 배정이 통째로 실패해도 HTTP 응답은 전부 201 이다.
#   k6 프로브는 이 구멍을 못 본다. 여기서 Redis 를 직접 세서 메운다.
#
# 세는 법: 활성 요청 HASH 에 partyId 필드가 있으면 배정까지 간 것이다
#          (create-or-check-party-untiered.lua:89 의 HSET userKey partyId).
set -eu
. "$(dirname "$0")/lib.sh"
PREFIX="${1:?RUN_ID 접두사가 필요하다 (예: f1757400000)}"

addr=$(sent SENTINEL get-master-addr-by-name mymaster | tr '\n' ':' | sed 's/:$//')
m=$(container_of_addr "$addr")
echo "새 master = ${m} (${addr})"

total=0; assigned=0; orphan=0
while read -r k; do
  [ -z "$k" ] && continue
  total=$((total+1))
  p=$(node "$m" HGET "$k" partyId 2>/dev/null | tr -d '\r')
  if [ -n "$p" ]; then assigned=$((assigned+1)); else orphan=$((orphan+1)); fi
done <<< "$(node "$m" --scan --pattern "qm:user:active-request:${PREFIX}*" 2>/dev/null)"

echo "활성 요청 키      : ${total}"
echo "배정까지 간 것    : ${assigned}"
echo "claim 만 되고 만 것: ${orphan}   <- HTTP 는 201 을 돌려준 조용한 실패"
echo
echo "주의: 승격된 replica 에 복제되지 못한 키는 이 집계에 아예 안 잡힌다."
echo "      k6 의 201 개수와 여기 total 의 차이가 '복제 전에 사라진 claim' 이다."
