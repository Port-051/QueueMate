#!/usr/bin/env bash
# clean.sh 와 동일한 대상. 단 컨테이너 내부에서 한 번에 처리해 docker exec 폭주를 피한다.
# qm:gameconfig:* 는 건드리지 않는다.
set -u
docker exec qm-redis sh -c '
for p in "qm:party*" "qm:user:active-request:*" "qm:proposal*"; do
  redis-cli --scan --pattern "$p" | xargs -r -n 2000 redis-cli UNLINK > /dev/null
done
echo "남은 non-gameconfig: $(redis-cli --scan --pattern "qm:*" | grep -vc "^qm:gameconfig:")"
echo "gameconfig: $(redis-cli --scan --pattern "qm:gameconfig:*" | wc -l)"
'
