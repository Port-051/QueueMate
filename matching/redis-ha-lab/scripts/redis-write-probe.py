#!/usr/bin/env python3
"""Sentinel 에게 master 를 물어 쓰기를 시도하는 프로브.

컨테이너의 busybox date 는 밀리초를 못 찍고, docker exec 는 호출마다
수십 ms 가 붙는다. 그래서 호스트에서 published 포트로 직접 붙는다.

한 tick 에서 하는 일 = Sentinel 인지 클라이언트가 하는 일과 같다.
  1) Sentinel 에게 지금 master 주소를 묻는다
  2) 그 주소에 SET 을 보낸다

각 tick 을 CSV 한 줄로 남긴다: t_ms,outcome,addr,detail
outcome 은 OK / FAIL_SENTINEL / FAIL_WRITE 셋이다.
FAIL_WRITE 안에는 READONLY(강등된 구 master 에 붙음)도 들어오므로 detail 로 구분한다.
"""
import socket, sys, time

SENTINELS = [("127.0.0.1", 26379), ("127.0.0.1", 26380), ("127.0.0.1", 26381)]
TICK = 0.2
TIMEOUT = 0.5


def encode(*args):
    out = b"*%d\r\n" % len(args)
    for a in args:
        b = a.encode() if isinstance(a, str) else a
        out += b"$%d\r\n%s\r\n" % (len(b), b)
    return out


def read_reply(f):
    line = f.readline()
    if not line:
        raise IOError("closed")
    t, rest = line[:1], line[1:-2]
    if t in b"+":
        return rest.decode()
    if t in b"-":
        raise RuntimeError(rest.decode())
    if t in b":":
        return int(rest)
    if t in b"$":
        n = int(rest)
        if n == -1:
            return None
        d = f.read(n + 2)[:-2]
        return d.decode()
    if t in b"*":
        n = int(rest)
        return [read_reply(f) for _ in range(n)]
    raise RuntimeError("bad reply %r" % line)


def call(host, port, *args):
    s = socket.create_connection((host, port), timeout=TIMEOUT)
    try:
        s.settimeout(TIMEOUT)
        s.sendall(encode(*args))
        return read_reply(s.makefile("rb"))
    finally:
        s.close()


def master_addr():
    for h, p in SENTINELS:
        try:
            r = call(h, p, "SENTINEL", "get-master-addr-by-name", "mymaster")
            if r:
                return r[0], int(r[1])
        except Exception:
            continue
    return None


def now_ms():
    return int(time.time() * 1000)


def main():
    dur = float(sys.argv[1]) if len(sys.argv) > 1 else 60.0
    out = open(sys.argv[2], "w") if len(sys.argv) > 2 else sys.stdout
    end = time.time() + dur
    i = 0
    while time.time() < end:
        t = now_ms()
        addr = master_addr()
        if addr is None:
            out.write("%d,FAIL_SENTINEL,,\n" % t)
        else:
            try:
                call(addr[0], addr[1], "SET", "qm:probe", str(t))
                out.write("%d,OK,%s:%d,\n" % (t, addr[0], addr[1]))
            except Exception as e:
                msg = str(e).replace(",", " ")[:80]
                out.write("%d,FAIL_WRITE,%s:%d,%s\n" % (t, addr[0], addr[1], msg))
        out.flush()
        i += 1
        time.sleep(max(0, TICK - (now_ms() - t) / 1000.0))
    if out is not sys.stdout:
        out.close()


main()
