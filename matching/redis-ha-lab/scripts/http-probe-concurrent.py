#!/usr/bin/env python3
"""동시 요청 프로브. 스레드 N개가 각자 일정 간격으로 매칭 신청을 보낸다.

단일 스레드 프로브는 장애 중 요청 하나가 2초씩 매달리는 바람에
실제로는 몇 건 못 보낸다. 그러면 "배정 단계에서 실패한 요청" 이 거의 안 생겨
재시도 큐가 할 일이 없다. 페일오버 구간에 요청을 충분히 밀어넣어야
claim 은 성공하고 배정만 실패하는 구간이 만들어진다.

사용: http-probe-concurrent.py <초> <출력csv> <태그> <스레드수>
"""
import json, sys, threading, time, urllib.request, urllib.error

BASE = "http://127.0.0.1:8080/api/v1/match-requests"
POS = ["TOP", "JUNGLE", "MID", "ADC", "SUPPORT"]
TICK = 0.2
TIMEOUT = 5.0

lock = threading.Lock()


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
        return e.code, e.read()[:40].decode("utf-8", "replace").replace(",", " ")
    except Exception as e:
        return 0, str(e)[:40].replace(",", " ")


def worker(wid, end, out, tag):
    i = 0
    while time.time() < end:
        t = int(time.time() * 1000)
        uid = "%s-w%02d-%05d" % (tag, wid, i)
        code, msg = post(uid, POS[(wid + i) % 5])
        with lock:
            out.write("%d,%s,%d,%s\n" % (t, uid, code, msg))
            out.flush()
        i += 1
        time.sleep(max(0, TICK - (int(time.time() * 1000) - t) / 1000.0))


def main():
    dur, path, tag = float(sys.argv[1]), sys.argv[2], sys.argv[3]
    n = int(sys.argv[4]) if len(sys.argv) > 4 else 10
    out = open(path, "w")
    end = time.time() + dur
    ts = [threading.Thread(target=worker, args=(w, end, out, tag)) for w in range(n)]
    for t in ts:
        t.start()
    for t in ts:
        t.join()
    out.close()


main()
