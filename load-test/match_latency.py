#!/usr/bin/env python3
"""매칭 '성사'까지 걸린 시간을 잰다.

HTTP 201 은 접수만 알려준다. 파티 배정은 @Async 로 뒤에서 돌기 때문에
성사 시각은 Redis 를 폴링해서 잡는다.

한 회(pair) 흐름
  1) A 가 TOP 으로 요청 -> 1인 파티가 생길 때까지 기다린다 (측정 대상 아님)
  2) B 의 userId 를 미리 정하고 폴러 스레드를 먼저 띄운다.
     폴러는 HTTP 응답을 기다리지 않고 t0 부터 바로 Redis 를 본다.
     (응답 뒤에 폴링을 시작하면 HTTP 응답시간이 그대로 측정 하한이 된다)
  3) t0 = B 의 HTTP 요청 직전. POST -> HTTP 응답 시각 t_http
  4) B 의 active-request 에 partyId 가 박히고 그 파티 인원이 2 가 되는 순간 = t_match
     인원은 파티 HASH 의 member: 필드 개수다. size 같은 카운터 필드는 없다 —
     join-party.lua 의 memberCount() 와 같은 방식(HKEYS 후 member: 접두사 세기)으로 센다
  측정값 = t_match - t0

측정 하한: 폴링 sleep 간격 + Redis RTT(호스트에서 약 0.11ms).
관측 불확실 구간(직전 폴링~성공 폴링)을 gap 으로 함께 기록한다.

2026-09-27 이후의 API 에 맞춘 것 (ltconfig.py · devjwt.py 참고)
  · 바디에 tier 가 있다(RANKED_SOLO 는 tierRule=EXIST). 값은 TIER 환경변수, 기본 GOLD_2.
    모든 부하 사용자가 한 티어라 색인은 격자의 칸 하나(needs:{포지션}:{TIER})만 쓴다.
  · userId 는 바디가 아니라 쿠키 qm_access(RS256 JWT)의 sub 다. 사용자 번호는 숫자여야 하므로
    "A{run}_{slot}_{i}" 같은 문자열 대신 ltconfig.run_base(RUN) 에서 시작하는 번호를 쓴다.
    토큰은 요청 직전(t0 이전)에 찍으므로 서명 시간(~1ms)은 측정에 안 들어간다.
  · Origin 헤더는 안 보낸다 — 없는 요청은 OriginCheckFilter 를 통과한다.
"""
import http.client
import os
import statistics
import sys
import tempfile
import threading
import time
import uuid
from multiprocessing import Process

sys.setswitchinterval(0.0005)   # 폴러 스레드가 GIL 을 오래 못 잡게 한다

import devjwt
import ltconfig
from resp import Resp

HOST, PORT = ltconfig.HOST, ltconfig.PORT
REDIS_HOST, REDIS_PORT = ltconfig.REDIS_HOST, ltconfig.REDIS_PORT

POLL_SLEEP = float(os.environ.get("POLL_SLEEP", "0.0"))
DISCARD    = int(os.environ.get("DISCARD", "0"))   # 슬롯당 앞 N회는 통계에서 뺀다
# 시간창 모드: 모든 슬롯이 T_END 까지 계속 돈다 -> 측정창 안에서 동시성이 일정하다.
# 라운드 수로 끊으면 끝에서 슬롯이 하나씩 빠지며 램프다운이 통계에 섞인다.
WARMUP     = float(os.environ.get("WARMUP", "0"))
DURATION   = float(os.environ.get("DURATION", "0"))
TIMEOUT    = float(os.environ.get("MATCH_TIMEOUT", "30"))
LABEL      = os.environ.get("LABEL", "run")
RUN        = os.environ.get("RUN_ID", uuid.uuid4().hex[:8])

# 사용자 번호: RUN_BASE + (slot * SLOT_STRIDE + i) * 2 → A, 그 다음 번호 → B.
# 슬롯 하나가 SLOT_STRIDE 회를 넘으면 번호가 다음 슬롯과 겹치므로 거기서 멈춘다.
RUN_BASE    = ltconfig.run_base(RUN)
SLOT_STRIDE = 100_000


def user_ids(slot, i):
    a = RUN_BASE + (slot * SLOT_STRIDE + i) * 2
    return str(a), str(a + 1)


def body(pos):
    return ltconfig.body(pos)


def post(conn, uid, pos):
    """uid 의 토큰은 여기 들어오기 전에 찍어 둔다(devjwt 캐시) — 호출부가 t0 를 재기 전에 mint 한다."""
    conn.request("POST", "/api/v1/match-requests", body(pos), ltconfig.headers(uid))
    r = conn.getresponse()
    r.read()
    return r.status


def poll_until(rd, uid, want, deadline, sleep=POLL_SLEEP):
    """uid 파티가 want 명이 될 때까지 폴링.
    반환 (성공시각, 폴링수, gap) — gap 은 직전 폴링 전송~성공 폴링 수신 구간(ms)."""
    ukey = "qm:user:active-request:" + uid
    pid, polls, prev = None, 0, time.time()
    while time.time() < deadline:
        t_send = time.time()
        if pid is None:
            v = rd.cmd("HGET", ukey, "partyId")
            polls += 1
            if v is None:
                prev = t_send
                if sleep:
                    time.sleep(sleep)
                continue
            pid = v.decode()
        fields = rd.cmd("HKEYS", "qm:party:" + pid)
        polls += 1
        now = time.time()
        sz = sum(1 for f in (fields or []) if f.startswith(b"member:"))
        if sz >= want:
            return now, polls, (now - prev) * 1000.0
        prev = t_send
        if sleep:
            time.sleep(sleep)
    return None, polls, 0.0


class Poller(threading.Thread):
    def __init__(self, rd, uid, want, timeout):
        super().__init__(daemon=True)
        self.rd, self.uid, self.want, self.timeout = rd, uid, want, timeout
        self.ready = threading.Event()
        self.t_match = self.polls = self.gap = None

    def run(self):
        self.rd.cmd("PING")          # 커넥션/스레드 예열
        self.ready.set()
        self.t_match, self.polls, self.gap = poll_until(
            self.rd, self.uid, self.want, time.time() + self.timeout)


class Runner(threading.Thread):
    def __init__(self, slot, rounds, out, prefill=False, t_start=0.0, t_end=0.0):
        super().__init__()
        self.slot, self.rounds, self.out, self.prefill = slot, rounds, out, prefill
        self.t_start, self.t_end = t_start, t_end

    def run(self):
        conn = http.client.HTTPConnection(HOST, PORT, timeout=60)
        rd_a = Resp(REDIS_HOST, REDIS_PORT)
        i = -1
        while True:
            i += 1
            if self.t_end:
                if time.time() >= self.t_end:
                    break
            elif i >= self.rounds:
                break
            if i >= SLOT_STRIDE:
                self.out.append(("id_space_exhausted", 0, 0, 0, 0, 0, time.time())); break
            a, b = user_ids(self.slot, i)
            try:
                devjwt.mint(a); devjwt.mint(b)   # 서명은 측정 밖에서
                if not self.prefill:
                    if post(conn, a, "TOP") != 201:
                        self.out.append(("http_a", 0, 0, 0, 0, 0, time.time())); continue
                    if poll_until(rd_a, a, 1, time.time() + TIMEOUT, 0.001)[0] is None:
                        self.out.append(("a_never_party", 0, 0, 0, 0, 0, time.time())); continue

                rd_b = Resp(REDIS_HOST, REDIS_PORT)
                p = Poller(rd_b, b, 2, TIMEOUT)
                p.start()
                p.ready.wait(5)

                t0 = time.time()
                st = post(conn, b, "JUNGLE")
                t_http = time.time()
                p.join(TIMEOUT + 5)
                rd_b.close()
                if st != 201:
                    self.out.append(("http_b/%d" % st, 0, 0, 0, 0, 0, time.time())); continue
                if p.t_match is None:
                    self.out.append(("timeout", 0, (t_http - t0) * 1000, 0, 0, 0, t0)); continue
                self.out.append(("ok", 201, (t_http - t0) * 1000,
                                 (p.t_match - t0) * 1000, p.polls, p.gap, t0))
                if (i < DISCARD) or (self.t_start and t0 < self.t_start):
                    self.out[-1] = ("warmup",) + self.out[-1][1:]
            except Exception as e:
                self.out.append(("exc:" + type(e).__name__, 0, 0, 0, 0, 0, time.time()))
                try:
                    conn.close()
                except Exception:
                    pass
                conn = http.client.HTTPConnection(HOST, PORT, timeout=60)
        rd_a.close()
        conn.close()


def pct(v, p):
    if not v:
        return 0.0
    k = min(len(v) - 1, max(0, int(round(len(v) * p / 100.0)) - 1))
    return v[k]


def child(procs_idx, slots, rounds, prefill, outpath, t_start=0.0, t_end=0.0):
    bufs, threads = [], []
    for s in slots:
        buf = []
        bufs.append(buf)
        threads.append(Runner(s, rounds, buf, prefill, t_start, t_end))
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    with open(outpath, "w") as f:
        for buf in bufs:
            for r in buf:
                f.write("%s\t%s\t%.4f\t%.4f\t%s\t%.4f\t%.6f\n" % r)


def main():
    conc    = int(os.environ.get("CONC", "1"))
    rounds  = int(os.environ.get("ROUNDS", "200"))
    procs   = int(os.environ.get("PROCS", "1"))
    prefill = os.environ.get("PREFILL", "0") == "1"
    procs = max(1, min(procs, conc))

    slots = [[] for _ in range(procs)]
    for s in range(conc):
        slots[s % procs].append(s)

    tmpd = tempfile.mkdtemp(prefix="ml_")
    ps = []
    t_begin = time.time()
    t_start = (t_begin + 2 + WARMUP) if DURATION else 0.0
    t_end   = (t_start + DURATION) if DURATION else 0.0
    for i, sl in enumerate(slots):
        pth = os.path.join(tmpd, "p%d.tsv" % i)
        p = Process(target=child, args=(i, sl, rounds, prefill, pth, t_start, t_end))
        p.start()
        ps.append((p, pth))
    for p, _ in ps:
        p.join()
    wall = time.time() - t_begin

    out = []
    for _, pth in ps:
        with open(pth) as f:
            for line in f:
                c = line.rstrip("\n").split("\t")
                out.append((c[0], c[1], float(c[2]), float(c[3]), int(c[4]), float(c[5]), float(c[6])))

    ok = [r for r in out if r[0] == "ok"]
    done = [r for r in out if r[0] in ("ok", "warmup")]
    http_ms  = sorted(r[2] for r in ok)
    match_ms = sorted(r[3] for r in ok)
    gaps     = sorted(r[5] for r in ok)
    delta    = sorted(r[3] - r[2] for r in ok)
    errs = {}
    for r in out:
        if r[0] not in ("ok", "warmup"):
            errs[r[0]] = errs.get(r[0], 0) + 1

    print(f"@@M@@ LABEL={LABEL} CONC={conc} PROCS={procs} ROUNDS={rounds} "
          f"PREFILL={int(prefill)} POLL_SLEEP_ms={POLL_SLEEP*1000:.2f} WARMUP={WARMUP:.0f} DUR={DURATION:.0f} "
          f"n_ok={len(ok)} n_err={len(out)-len(done)} wall={wall:.1f}s "
          f"pairs/s={(len(ok)/DURATION if DURATION else len(done)/wall):.0f} "
          f"req/s={(len(ok)*2/DURATION if DURATION else len(done)*2/wall):.0f}\n"
          f"    MATCH p50={pct(match_ms,50):.2f} p95={pct(match_ms,95):.2f} "
          f"p99={pct(match_ms,99):.2f} max={(match_ms[-1] if match_ms else 0):.2f} "
          f"mean={(statistics.mean(match_ms) if match_ms else 0):.2f}\n"
          f"    HTTP  p50={pct(http_ms,50):.2f} p95={pct(http_ms,95):.2f} "
          f"p99={pct(http_ms,99):.2f} max={(http_ms[-1] if http_ms else 0):.2f}\n"
          f"    DELTA(match-http) p50={pct(delta,50):.2f} p95={pct(delta,95):.2f} "
          f"p99={pct(delta,99):.2f} max={(delta[-1] if delta else 0):.2f}\n"
          f"    GAP(폴링 불확실구간) p50={pct(gaps,50):.3f} p95={pct(gaps,95):.3f} "
          f"max={(gaps[-1] if gaps else 0):.3f}  errs={errs}")

    if os.environ.get("RAW"):
        with open(os.environ["RAW"], "w") as f:
            for r in out:
                f.write("%s\t%s\t%.3f\t%.3f\t%s\t%.3f\t%.6f\n" % r)


if __name__ == "__main__":
    main()
