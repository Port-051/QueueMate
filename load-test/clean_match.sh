#!/bin/bash
# qm:gameconfig:* 은 절대 건드리지 않는다
for p in 'qm:party:*' 'qm:user:active-request:*'; do
  docker exec qm-redis redis-cli --scan --pattern "$p" | xargs -r -n 500 docker exec qm-redis redis-cli DEL > /dev/null
done
echo "dbsize=$(docker exec qm-redis redis-cli dbsize) gameconfig=$(docker exec qm-redis redis-cli --scan --pattern 'qm:gameconfig:*' | wc -l)"
