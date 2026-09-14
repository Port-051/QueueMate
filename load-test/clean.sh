#!/usr/bin/env bash
# Redis 정리. qm:gameconfig:* 는 절대 지우지 않는다.
set -u
for pat in 'qm:party*' 'qm:user:active-request:*' 'qm:proposal*'; do
  docker exec qm-redis redis-cli --scan --pattern "$pat" \
    | xargs -r -n 400 docker exec qm-redis redis-cli DEL > /dev/null
done
echo "남은 non-gameconfig 키: $(docker exec qm-redis redis-cli --scan --pattern 'qm:*' | grep -vc '^qm:gameconfig:')"
echo "gameconfig 키: $(docker exec qm-redis redis-cli --scan --pattern 'qm:gameconfig:*' | wc -l)"
