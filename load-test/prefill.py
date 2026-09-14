#!/usr/bin/env python3
"""대기 색인을 깊게 만든다. TOP 대기자 N 명을 넣으면
needs:JUNGLE ZSET 에 1인 파티 N 개가 쌓인다."""
import http.client, json, os, sys, time, uuid
from resp import Resp

N = int(sys.argv[1]) if len(sys.argv) > 1 else 100
TAG = "W" + uuid.uuid4().hex[:6]
c = http.client.HTTPConnection("127.0.0.1", 8080, timeout=30)
for i in range(N):
    b = json.dumps({"userId": f"{TAG}_{i}", "game": "LOL", "modeKey": "RANKED_SOLO",
                    "keyCondition": {"type": "POSITION", "value": "TOP"},
                    "voicePreference": "REQUIRED", "playPurpose": "RANK_UP"})
    c.request("POST", "/api/v1/match-requests", b, {"Content-Type": "application/json"})
    r = c.getresponse(); r.read()
    if r.status != 201:
        print("prefill fail", r.status); sys.exit(1)
    time.sleep(0.004)   # @Async 배정이 순서대로 끝나게 천천히 넣는다
rd = Resp()
key = "qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP:needs:JUNGLE"
for _ in range(50):
    d = rd.cmd("ZCARD", key)
    if d >= N: break
    time.sleep(0.1)
print("needs:JUNGLE depth =", rd.cmd("ZCARD", key))
