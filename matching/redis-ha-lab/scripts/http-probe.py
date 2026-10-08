#!/usr/bin/env python3
"""앱 API 에 매칭 신청을 일정 도착률로 쏘고, 나중에 실제 배정 여부를 따로 확인한다.

핵심은 두 지표를 나눠 재는 것이다.
  HTTP 결과   컨트롤러가 뭘 돌려줬나 (201 / 503 / 5xx)
  배정 결과   진짜로 파티에 들어갔나 (Redis 의 active-request 에 partyId 가 있나)

@Async 로 도는 배정이 실패해도 HTTP 는 이미 201 이 나간 뒤라,
이 둘이 어긋나는 구간이 곧 "조용히 사라진 요청" 이다.
"""
import json, socket, sys, time, urllib.request, urllib.error

BASE = "http://127.0.0.1:8080/api/v1/match-requests"
POS = ["TOP", "JUNGLE", "MID", "ADC", "SUPPORT"]
TICK = 0.2
TIMEOUT = 3.0


def post(uid, pos):
    body = json.dumps({
        "userId": uid, "game": "LOL", "modeKey": "NORMAL_5",
        "keyCondition": {"type": "POSITION", "value": pos},
        "voicePreference": "REQUIRED", "playPurpose": "RANK_UP",
    }).encode()
    req = urllib.request.Request(BASE, data=body,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
            return r.status, ""
    except urllib.error.HTTPError as e:
        return e.code, e.read()[:60].decode("utf-8", "replace").replace(",", " ")
    except Exception as e:
        return 0, str(e)[:60].replace(",", " ")


def main():
    dur = float(sys.argv[1])
    out = open(sys.argv[2], "w")
    tag = sys.argv[3]
    end = time.time() + dur
    i = 0
    while time.time() < end:
        t = int(time.time() * 1000)
        uid = "%s-%05d" % (tag, i)
        code, msg = post(uid, POS[i % 5])
        out.write("%d,%s,%d,%s\n" % (t, uid, code, msg))
        out.flush()
        i += 1
        time.sleep(max(0, TICK - (int(time.time() * 1000) - t) / 1000.0))
    out.close()


main()
