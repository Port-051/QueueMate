#!/usr/bin/env python3
"""HTTP 프로브가 보낸 요청들이 실제로 배정됐는지 Redis 에서 직접 확인한다.

HTTP 201 은 "대기열에 넣었다" 까지만 뜻한다. 배정은 @Async 로 뒤에 돈다.
그래서 진짜 결과는 Redis 를 봐야 안다.

  qm:user:active-request:{userId} 가
    없다              -> 유실 (TTL 60초 만료. 배정도 못 받고 사라졌다)
    있는데 partyId 없음 -> 아직 대기 중
    partyId 있음       -> 배정 성공
"""
import socket, sys, collections

SENTINELS = [("127.0.0.1", 26379), ("127.0.0.1", 26380), ("127.0.0.1", 26381)]


def encode(*a):
    o = b"*%d\r\n" % len(a)
    for x in a:
        b = x.encode() if isinstance(x, str) else x
        o += b"$%d\r\n%s\r\n" % (len(b), b)
    return o


def read(f):
    line = f.readline()
    t, rest = line[:1], line[1:-2]
    if t == b"+": return rest.decode()
    if t == b"-": raise RuntimeError(rest.decode())
    if t == b":": return int(rest)
    if t == b"$":
        n = int(rest)
        return None if n == -1 else f.read(n + 2)[:-2].decode()
    if t == b"*":
        n = int(rest)
        return [read(f) for _ in range(n)]
    raise RuntimeError(line)


class Conn:
    def __init__(s, h, p):
        s.s = socket.create_connection((h, p), timeout=3)
        s.f = s.s.makefile("rb")
    def call(s, *a):
        s.s.sendall(encode(*a)); return read(s.f)


def master():
    for h, p in SENTINELS:
        try:
            c = Conn(h, p)
            r = c.call("SENTINEL", "get-master-addr-by-name", "mymaster")
            return r[0], int(r[1])
        except Exception:
            continue
    raise SystemExit("Sentinel 없음")


def main():
    csv, t0f = sys.argv[1], sys.argv[2]
    t0 = int(open(t0f).read().strip())
    rows = []
    for line in open(csv):
        p = line.rstrip("\n").split(",", 3)
        if len(p) >= 3:
            rows.append((int(p[0]), p[1], int(p[2])))

    h, p = master()
    c = Conn(h, p)
    print("현재 master = %s:%d" % (h, p))
    print()

    http = collections.Counter()
    assign = collections.Counter()
    per_state = []
    for t, uid, code in rows:
        http[code] += 1
        key = "qm:user:active-request:" + uid
        if c.call("EXISTS", key) == 0:
            st = "유실"
        elif c.call("HGET", key, "partyId"):
            st = "배정성공"
        else:
            st = "대기중"
        assign[st] += 1
        per_state.append((t, uid, code, st))

    n = len(rows)
    print("총 요청 %d건" % n)
    print()
    print("[HTTP 응답]")
    for k, v in sorted(http.items()):
        print("  %-6s %4d건  (%5.1f%%)" % (k if k else "conn실패", v, 100.0 * v / n))
    print()
    print("[실제 배정 결과]")
    for k in ("배정성공", "대기중", "유실"):
        v = assign[k]
        print("  %-8s %4d건  (%5.1f%%)" % (k, v, 100.0 * v / n))
    print()
    print("[t0 기준 구간별]  t0=%d" % t0)
    print("  %-14s %-8s %-8s %s" % ("구간", "요청", "HTTP201", "배정성공"))
    for lo, hi, label in [(-10**9, 0, "t0 이전"), (0, 10000, "t0~+10초"),
                          (10000, 30000, "+10~30초"), (30000, 10**9, "+30초~")]:
        seg = [r for r in per_state if lo <= r[0] - t0 < hi]
        if not seg: continue
        ok201 = sum(1 for r in seg if r[2] == 201)
        okasg = sum(1 for r in seg if r[3] == "배정성공")
        print("  %-14s %-8d %-8d %d" % (label, len(seg), ok201, okasg))
    print()
    print("[HTTP 는 성공했는데 배정이 안 된 요청]")
    bad = [r for r in per_state if r[2] == 201 and r[3] != "배정성공"]
    print("  %d건 (전체의 %.1f%%)" % (len(bad), 100.0 * len(bad) / n))
    if bad:
        print("  첫 건: t0 %+d ms  %s  -> %s" % (bad[0][0] - t0, bad[0][1], bad[0][3]))
        print("  끝 건: t0 %+d ms  %s  -> %s" % (bad[-1][0] - t0, bad[-1][1], bad[-1][3]))


main()
