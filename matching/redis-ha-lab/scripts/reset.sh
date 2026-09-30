#!/usr/bin/env bash
# 판을 통째로 새로 깐다. named volume 까지 지운다.
#
# 이걸 써야 하는 때:
#   - 페일오버를 여러 번 돌려서 지금 누가 master 인지 헷갈릴 때
#   - Sentinel 이 유령 노드를 known-sentinel 에 물고 있을 때
#   - down-after / quorum 을 바꿔서 실험 3 의 다음 조건으로 넘어갈 때
#
# 볼륨을 안 지우면 Sentinel 이 이전 실험의 master 를 그대로 감시한다
# (sentinel/entrypoint.sh 주석).
set -eu
. "$(dirname "$0")/lib.sh"
dc down -v --remove-orphans
echo "볼륨까지 지웠다. ./scripts/up.sh 로 다시 띄워라"
