#!/usr/bin/env bash
# Redis 정리. qm:gameconfig:* 는 절대 지우지 않는다.
# qm:user:active-room:* 도 건드리지 않는다 — app:platform 의 입장 표시 키다(CLAUDE.md §1). 이 앱이 만든 키만 지운다:
#   qm:party:* (파티 HASH · needs 색인 · needs-roles) / qm:user:active-request:* / qm:proposal:* (accepts SET · pending ZSET) / qm:lock:pool:* (Redisson 풀 락)
set -u
for pat in 'qm:party*' 'qm:user:active-request:*' 'qm:proposal*' 'qm:lock:pool:*'; do
  docker exec qm-redis redis-cli --scan --pattern "$pat" \
    | xargs -r -n 400 docker exec qm-redis redis-cli DEL > /dev/null
done
echo "남은 non-gameconfig 키: $(docker exec qm-redis redis-cli --scan --pattern 'qm:*' | grep -vc '^qm:gameconfig:')"
echo "gameconfig 키: $(docker exec qm-redis redis-cli --scan --pattern 'qm:gameconfig:*' | wc -l)"
