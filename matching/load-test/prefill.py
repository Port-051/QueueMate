#!/usr/bin/env python3
"""대기 색인을 깊게 만든다. TOP 대기자 N 명을 넣으면 needs:JUNGLE:{TIER} 칸에 1인 파티 N 개가 쌓인다.

  python3 prefill.py 100

바디(tier 포함) · 쿠키 · 색인 키는 ltconfig.py 가 정한다 — 티어 모드는 Lua 가 needs 키 뒤에 ':{tier}'
를 붙이므로 예전의 needs:JUNGLE 은 항상 비어 있다. 사용자 번호는 match_latency.py 와 겹치지 않게
자기 실행 구간(run_base) 의 뒤쪽 절반(+5억)을 쓴다.
"""
import http.client, os, sys, time, uuid
import devjwt
import ltconfig
from resp import Resp

N = int(sys.argv[1]) if len(sys.argv) > 1 else 100
BASE = ltconfig.run_base(os.environ.get("PREFILL_RUN_ID", uuid.uuid4().hex[:8])) + ltconfig.RUN_SPAN // 2
c = http.client.HTTPConnection(ltconfig.HOST, ltconfig.PORT, timeout=30)
for i in range(N):
    uid = str(BASE + i)
    devjwt.mint(uid)
    c.request("POST", "/api/v1/match-requests", ltconfig.body("TOP"), ltconfig.headers(uid))
    r = c.getresponse(); r.read()
    if r.status != 201:
        print("prefill fail", r.status); sys.exit(1)
    time.sleep(0.004)   # @Async 배정이 순서대로 끝나게 천천히 넣는다
rd = Resp(ltconfig.REDIS_HOST, ltconfig.REDIS_PORT)
key = ltconfig.needs_key("JUNGLE")
for _ in range(50):
    d = rd.cmd("ZCARD", key)
    if d >= N: break
    time.sleep(0.1)
print(f"{key} depth =", rd.cmd("ZCARD", key))
