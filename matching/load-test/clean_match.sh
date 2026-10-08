#!/bin/bash
# qm:gameconfig:* 은 절대 건드리지 않는다. qm:user:active-room:* 도(app:platform 의 키).
# qm:proposal* 은 정원이 찬 파티의 제안 흔적(accepts SET · pending ZSET)이라 같이 지운다 — 남기면 스위퍼가 없는 파티를 계속 꺼낸다.
for p in 'qm:party:*' 'qm:user:active-request:*' 'qm:proposal*' 'qm:lock:pool:*'; do
  docker exec qm-redis redis-cli --scan --pattern "$p" | xargs -r -n 500 docker exec qm-redis redis-cli DEL > /dev/null
done
echo "dbsize=$(docker exec qm-redis redis-cli dbsize) gameconfig=$(docker exec qm-redis redis-cli --scan --pattern 'qm:gameconfig:*' | wc -l)"
